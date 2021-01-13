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
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.ipc.decoders.CompositeMetadataDecoder;
import io.rsocket.ipc.exception.RouteNotFound;
import io.rsocket.ipc.util.IPCChannelFunction;
import io.rsocket.ipc.util.IPCFunction;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RoutingServerRSocket implements RSocket {

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
      final MetadataDecoder.Metadata decodedMetadata = this.decoder.decode(payload.sliceMetadata());

      final String route = decodedMetadata.route();
      final IPCFunction<Mono<Void>> monoIPCFunction = this.router.routeFireAndForget(route);

      if (monoIPCFunction == null) {
        return Mono.error(new RouteNotFound("Nothing found for route " + route));
      }

      final Mono<Void> response = monoIPCFunction.apply(payload, decodedMetadata);

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
      final MetadataDecoder.Metadata decodedMetadata = this.decoder.decode(payload.sliceMetadata());

      final String route = decodedMetadata.route();
      final IPCFunction<Mono<Payload>> monoIPCFunction = this.router.routeRequestResponse(route);

      if (monoIPCFunction == null) {
        return Mono.error(new NullPointerException("nothing found for route " + route));
      }

      final Mono<Payload> response = monoIPCFunction.apply(payload, decodedMetadata);

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
      final MetadataDecoder.Metadata decodedMetadata = this.decoder.decode(payload.sliceMetadata());

      final String route = decodedMetadata.route();
      final IPCFunction<Flux<Payload>> ffContext = this.router.routeRequestStream(route);

      if (ffContext == null) {
        return Flux.error(new NullPointerException("nothing found for route " + route));
      }

      final Flux<Payload> response = ffContext.apply(payload, decodedMetadata);

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
              final Payload payload = firstSignal.get();

              if (payload != null) {
                return doRequestChannel(payload, flux);
              }

              return flux;
            });
  }

  private Flux<Payload> doRequestChannel(Payload payload, Flux<Payload> payloadFlux) {
    try {
      final MetadataDecoder.Metadata decodedMetadata = this.decoder.decode(payload.sliceMetadata());

      final String route = decodedMetadata.route();
      final IPCChannelFunction ffContext = this.router.routeRequestChannel(route);

      if (ffContext == null) {
        payload.release();
        return Flux.error(new NullPointerException("nothing found for route " + route));
      }

      return ffContext.apply(payloadFlux, payload, decodedMetadata);
    } catch (Throwable t) {
      payload.release();
      return Flux.error(t);
    }
  }
}
