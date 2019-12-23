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

import io.opentracing.Tracer;
import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.ResponderRSocket;
import io.rsocket.ipc.decoders.CompositeMetadataDecoder;
import io.rsocket.ipc.exception.ServiceNotFound;
import io.rsocket.ipc.util.IPCChannelFunction;
import io.rsocket.ipc.util.IPCFunction;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SuppressWarnings("unchecked")
public class RoutingServerRSocket extends AbstractRSocket implements ResponderRSocket {

  final Router router;
  final MetadataDecoder decoder;

  public RoutingServerRSocket(Router router) {
    this(new CompositeMetadataDecoder(), router);
  }

  public RoutingServerRSocket(Tracer tracer, Router router) {
    this(new CompositeMetadataDecoder(tracer), router);
  }

  public RoutingServerRSocket(MetadataDecoder decoder, Router router) {
    this.decoder = decoder;
    this.router = router;
  }

  @Override
  public Mono<Void> fireAndForget(Payload payload) {
    try {
      MetadataDecoder.Metadata decodedMetadata = decoder.decode(payload);

      IPCFunction<Mono<Void>> monoIPCFunction =
          this.router.routeFireAndForget(decodedMetadata.route);

      if (monoIPCFunction == null) {
        return Mono.error(new ServiceNotFound("Nothing found for route " + decodedMetadata.route));
      }

      Mono<Void> response =
          monoIPCFunction.apply(payload, decodedMetadata.metadata, decodedMetadata.spanContext);

      payload.release();

      return response;
    } catch (Throwable t) {
      payload.release();
      return Mono.error(t);
    }
  }

  @Override
  public Mono<Payload> requestResponse(Payload payload) {
    try {
      MetadataDecoder.Metadata decodedMetadata = decoder.decode(payload);

      IPCFunction<Mono<Payload>> monoIPCFunction =
          this.router.routeRequestResponse(decodedMetadata.route);

      if (monoIPCFunction == null) {
        return Mono.error(
            new NullPointerException("nothing found for route " + decodedMetadata.route));
      }

      Mono<Payload> response =
          monoIPCFunction.apply(payload, decodedMetadata.metadata, decodedMetadata.spanContext);

      payload.release();

      return response;
    } catch (Throwable t) {
      payload.release();
      return Mono.error(t);
    }
  }

  @Override
  public Flux<Payload> requestStream(Payload payload) {
    try {
      MetadataDecoder.Metadata decodedMetadata = decoder.decode(payload);

      IPCFunction<Flux<Payload>> ffContext = this.router.routeRequestStream(decodedMetadata.route);

      if (ffContext == null) {
        return Flux.error(
            new NullPointerException("nothing found for route " + decodedMetadata.route));
      }

      Flux<Payload> response =
          ffContext.apply(payload, decodedMetadata.metadata, decodedMetadata.spanContext);

      payload.release();

      return response;
    } catch (Throwable t) {
      payload.release();
      return Flux.error(t);
    }
  }

  @Override
  public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
    return Flux.from(payloads)
        .switchOnFirst(
            (firstSignal, flux) -> {
              Payload payload = firstSignal.get();
              if (payload != null) {
                try {
                  MetadataDecoder.Metadata decodedMetadata = decoder.decode(payload);
                  IPCChannelFunction ffContext =
                      this.router.routeRequestChannel(decodedMetadata.route);

                  if (ffContext == null) {
                    payload.release();
                    return Flux.error(
                        new NullPointerException(
                            "nothing found for route " + decodedMetadata.route));
                  }

                  return ffContext.apply(
                      flux, payload, decodedMetadata.metadata, decodedMetadata.spanContext);
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
      MetadataDecoder.Metadata decodedMetadata = decoder.decode(payload);
      IPCChannelFunction ffContext = this.router.routeRequestChannel(decodedMetadata.route);

      if (ffContext == null) {
        payload.release();
        return Flux.error(
            new NullPointerException("nothing found for route " + decodedMetadata.route));
      }

      return ffContext.apply(
          Flux.from(payloads), payload, decodedMetadata.metadata, decodedMetadata.spanContext);
    } catch (Throwable t) {
      payload.release();
      return Flux.error(t);
    }
  }
}
