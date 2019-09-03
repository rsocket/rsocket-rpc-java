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
import io.netty.buffer.ByteBufAllocator;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.rpc.frames.Metadata;
import io.rsocket.util.ByteBufPayload;
import java.util.Objects;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SuppressWarnings("unchecked")
public final class Client<I, O> {

  private final String service;
  private final Marshaller<I> marshaller;
  private final Unmarsaller<O> unmarshaller;
  private final RSocket rSocket;

  private Client(
      final String service,
      final Marshaller marshaller,
      final Unmarsaller unmarshaller,
      final RSocket rSocket) {
    this.service = service;
    this.marshaller = marshaller;
    this.unmarshaller = unmarshaller;
    this.rSocket = rSocket;
  }

  public interface R {
    P rsocket(RSocket rSocket);
  }

  public interface P {
    <I> U<I> marshall(Marshaller<I> marshaller);
  }

  public interface U<I> {
    <O> Client<I, O> unmarshall(Unmarsaller<O> unmarshaller);
  }

  public Functions.RequestResponse<I, O> requestResponse(String route) {
    Objects.requireNonNull(marshaller);
    Objects.requireNonNull(unmarshaller);
    Objects.requireNonNull(rSocket);
    return (o, byteBuf) ->
        doRequestResponse(service, route, rSocket, marshaller, unmarshaller, o, byteBuf);
  }

  public Functions.RequestChannel<I, O> requestChannel(String route) {
    Objects.requireNonNull(marshaller);
    Objects.requireNonNull(unmarshaller);
    Objects.requireNonNull(rSocket);
    return (publisher, byteBuf) ->
        doRequestChannel(service, route, rSocket, marshaller, unmarshaller, publisher, byteBuf);
  }

  public Functions.RequestStream<I, O> requestStream(String route) {
    Objects.requireNonNull(marshaller);
    Objects.requireNonNull(unmarshaller);
    Objects.requireNonNull(rSocket);
    return (o, byteBuf) ->
        doRequestStream(service, route, rSocket, marshaller, unmarshaller, o, byteBuf);
  }

  public Functions.FireAndForget<I> fireAndForget(String route) {
    Objects.requireNonNull(marshaller);
    Objects.requireNonNull(unmarshaller);
    Objects.requireNonNull(rSocket);
    return (o, byteBuf) -> doFireAndForget(service, route, rSocket, marshaller, o, byteBuf);
  }

  private static class Builder implements P, U, R {
    private final String service;
    private Marshaller marshaller;
    private Unmarsaller unmarshaller;
    private RSocket rSocket;

    private Builder(String service) {
      this.service = service;
    }

    @Override
    public <I> U<I> marshall(Marshaller<I> marshaller) {
      Objects.requireNonNull(marshaller);
      this.marshaller = marshaller;
      return this;
    }

    @Override
    public Client unmarshall(Unmarsaller unmarshaller) {
      Objects.requireNonNull(unmarshaller);
      this.unmarshaller = unmarshaller;
      return new Client(service, marshaller, unmarshaller, rSocket);
    }

    @Override
    public P rsocket(RSocket rSocket) {
      Objects.requireNonNull(rSocket);
      this.rSocket = rSocket;
      return this;
    }
  }

  private Mono<Void> doFireAndForget(
      final String service,
      final String route,
      final RSocket r,
      final Marshaller<I> marshaller,
      final I o,
      final ByteBuf metadata) {
    try {
      ByteBuf d = marshaller.apply(o);
      ByteBuf m = Metadata.encode(ByteBufAllocator.DEFAULT, service, route, metadata);

      Payload payload = ByteBufPayload.create(d, m);
      return r.fireAndForget(payload);
    } catch (Throwable t) {
      return Mono.error(t);
    }
  }

  private Mono<O> doRequestResponse(
      final String service,
      final String route,
      final RSocket r,
      final Marshaller<I> marshaller,
      final Unmarsaller<O> unmarsaller,
      final I o,
      final ByteBuf metadata) {
    try {
      ByteBuf d = marshaller.apply(o);
      ByteBuf m = Metadata.encode(ByteBufAllocator.DEFAULT, service, route, metadata);

      Payload payload = ByteBufPayload.create(d, m);
      return r.requestResponse(payload)
          .map(
              p -> {
                try {
                  return unmarsaller.apply(p.sliceData());
                } finally {
                  p.release();
                }
              });
    } catch (Throwable t) {
      return Mono.error(t);
    }
  }

  private Flux<O> doRequestStream(
      final String service,
      final String route,
      final RSocket r,
      final Marshaller<I> marshaller,
      final Unmarsaller<O> unmarsaller,
      final I o,
      final ByteBuf metadata) {
    try {
      ByteBuf d = marshaller.apply(o);
      ByteBuf m = Metadata.encode(ByteBufAllocator.DEFAULT, service, route, metadata);

      Payload payload = ByteBufPayload.create(d, m);
      return r.requestStream(payload)
          .map(
              p -> {
                try {
                  return unmarsaller.apply(p.sliceData());
                } finally {
                  p.release();
                }
              });
    } catch (Throwable t) {
      return Flux.error(t);
    }
  }

  private Flux<O> doRequestChannel(
      final String service,
      final String route,
      final RSocket r,
      final Marshaller<I> marshaller,
      final Unmarsaller<O> unmarsaller,
      final Publisher<I> pub,
      final ByteBuf metadata) {
    try {
      Flux<Payload> input =
          Flux.from(pub)
              .map(
                  o -> {
                    ByteBuf d = marshaller.apply(o);
                    ByteBuf m = Metadata.encode(ByteBufAllocator.DEFAULT, service, route, metadata);

                    return ByteBufPayload.create(d, m);
                  });

      return r.requestChannel(input)
          .map(
              p -> {
                try {
                  return unmarsaller.apply(p.sliceData());
                } finally {
                  p.release();
                }
              });

    } catch (Throwable t) {
      return Flux.error(t);
    }
  }

  public static R service(String service) {
    Objects.requireNonNull(service);
    return new Builder(service);
  }
}
