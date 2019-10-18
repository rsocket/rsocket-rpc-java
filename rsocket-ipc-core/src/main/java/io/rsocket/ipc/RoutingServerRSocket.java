/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.rsocket.ipc;

import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.ResponderRSocket;
import io.rsocket.ipc.decoders.CompositeMetadataDecoder;
import io.rsocket.ipc.util.IPCChannelFunction;
import io.rsocket.ipc.util.IPCFunction;
import java.util.Map;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SuppressWarnings("unchecked")
class RoutingServerRSocket extends AbstractRSocket implements ResponderRSocket {

  final Map<String, IPCFunction<Mono<Void>>> fireAndForgetRegistry;
  final Map<String, IPCFunction<Mono<Payload>>> requestResponseRegistry;
  final Map<String, IPCFunction<Flux<Payload>>> requestStreamRegistry;
  final Map<String, IPCChannelFunction> requestChannelRegistry;

  final MetadataDecoder decoder;

  public RoutingServerRSocket(
      Map<String, IPCFunction<Mono<Void>>> fireAndForgetRegistry,
      Map<String, IPCFunction<Mono<Payload>>> requestResponseRegistry,
      Map<String, IPCFunction<Flux<Payload>>> requestStreamRegistry,
      Map<String, IPCChannelFunction> requestChannelRegistry) {
    this(
        new CompositeMetadataDecoder(),
        fireAndForgetRegistry,
        requestResponseRegistry,
        requestStreamRegistry,
        requestChannelRegistry);
  }

  public RoutingServerRSocket(
      Tracer tracer,
      Map<String, IPCFunction<Mono<Void>>> fireAndForgetRegistry,
      Map<String, IPCFunction<Mono<Payload>>> requestResponseRegistry,
      Map<String, IPCFunction<Flux<Payload>>> requestStreamRegistry,
      Map<String, IPCChannelFunction> requestChannelRegistry) {
    this(
        new CompositeMetadataDecoder(tracer),
        fireAndForgetRegistry,
        requestResponseRegistry,
        requestStreamRegistry,
        requestChannelRegistry);
  }

  public RoutingServerRSocket(
      MetadataDecoder decoder,
      Map<String, IPCFunction<Mono<Void>>> fireAndForgetRegistry,
      Map<String, IPCFunction<Mono<Payload>>> requestResponseRegistry,
      Map<String, IPCFunction<Flux<Payload>>> requestStreamRegistry,
      Map<String, IPCChannelFunction> requestChannelRegistry) {
    this.decoder = decoder;
    this.fireAndForgetRegistry = fireAndForgetRegistry;
    this.requestResponseRegistry = requestResponseRegistry;
    this.requestStreamRegistry = requestStreamRegistry;
    this.requestChannelRegistry = requestChannelRegistry;
  }

  @Override
  public Mono<Void> fireAndForget(Payload payload) {
    try {
      Mono<Void> response = decoder.decode(payload, this::doDecodeAndHandleFireAndForget);

      payload.release();

      return response;
    } catch (Throwable t) {
      payload.release();
      return Mono.error(t);
    }
  }

  Mono<Void> doDecodeAndHandleFireAndForget(
      ByteBuf data, ByteBuf metadata, String route, SpanContext spanContext) throws Exception {
    IPCFunction<Mono<Void>> monoIPCFunction = this.fireAndForgetRegistry.get(route);

    if (monoIPCFunction == null) {
      return Mono.error(new NullPointerException("nothing found for route " + route));
    }

    return monoIPCFunction.apply(data, metadata, spanContext);
  }

  @Override
  public Mono<Payload> requestResponse(Payload payload) {
    try {
      Mono<Payload> response = decoder.decode(payload, this::doDecodeAndHandleRequestResponse);

      payload.release();

      return response;
    } catch (Throwable t) {
      payload.release();
      return Mono.error(t);
    }
  }

  Mono<Payload> doDecodeAndHandleRequestResponse(
      ByteBuf data, ByteBuf metadata, String route, SpanContext spanContext) throws Exception {
    IPCFunction<Mono<Payload>> monoIPCFunction = this.requestResponseRegistry.get(route);

    if (monoIPCFunction == null) {
      return Mono.error(new NullPointerException("nothing found for route " + route));
    }

    return monoIPCFunction.apply(data, metadata, spanContext);
  }

  @Override
  public Flux<Payload> requestStream(Payload payload) {
    try {
      Flux<Payload> response = decoder.decode(payload, this::doDecodeAndHandleRequestStream);

      payload.release();

      return response;
    } catch (Throwable t) {
      payload.release();
      return Flux.error(t);
    }
  }

  public Flux<Payload> doDecodeAndHandleRequestStream(
      ByteBuf data, ByteBuf metadata, String route, SpanContext spanContext) throws Exception {
    IPCFunction<Flux<Payload>> ffContext = this.requestStreamRegistry.get(route);

    if (ffContext == null) {
      return Flux.error(new NullPointerException("nothing found for route " + route));
    }

    return ffContext.apply(data, metadata, spanContext);
  }

  @Override
  public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
    return Flux.from(payloads)
        .switchOnFirst(
            (firstSignal, flux) -> {
              if (firstSignal.hasValue()) {
                Payload payload = firstSignal.get();
                try {
                  return decoder.decode(
                      payload,
                      (ByteBuf data, ByteBuf metadata, String route, SpanContext spanContext) -> {
                        IPCChannelFunction ffContext = this.requestChannelRegistry.get(route);

                        if (ffContext == null) {
                          payload.release();
                          return Flux.error(
                              new NullPointerException("nothing found for route " + route));
                        }

                        return ffContext.apply(flux, data, metadata, spanContext);
                      });
                } catch (Throwable t) {
                  payload.release();
                  return Flux.error(t);
                }
              }

              return flux;
            });
  }

  @Override
  public Flux<Payload> requestChannel(Payload payload, Publisher<Payload> payloads) {
    try {
      return decoder.decode(
          payload,
          (data, metadata, route, spanContext) -> {
            IPCChannelFunction ffContext = this.requestChannelRegistry.get(route);

            if (ffContext == null) {
              payload.release();
              return Flux.error(new NullPointerException("nothing found for route " + route));
            }

            return ffContext.apply(Flux.from(payloads), data, metadata, spanContext);
          });
    } catch (Throwable t) {
      payload.release();
      return Flux.error(t);
    }
  }
}
