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
import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.ipc.util.TriFunction;
import io.rsocket.rpc.frames.Metadata;
import io.rsocket.util.ByteBufPayload;
import java.util.Map;
import java.util.function.BiFunction;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SuppressWarnings("unchecked")
class IPCServerRSocket extends AbstractRSocket implements IPCRSocket {
  private final String service;
  private final Marshaller marshaller;
  private final Unmarsaller unmarshaller;

  private final Map<String, BiFunction<Object, ByteBuf, Mono>> rr;
  private final Map<String, TriFunction<Object, Publisher, ByteBuf, Flux>> rc;
  private final Map<String, BiFunction<Object, ByteBuf, Flux>> rs;
  private final Map<String, BiFunction<Object, ByteBuf, Mono<Void>>> ff;

  IPCServerRSocket(
      String service,
      Marshaller marshaller,
      Unmarsaller unmarshaller,
      Map<String, BiFunction<Object, ByteBuf, Mono>> rr,
      Map<String, TriFunction<Object, Publisher, ByteBuf, Flux>> rc,
      Map<String, BiFunction<Object, ByteBuf, Flux>> rs,
      Map<String, BiFunction<Object, ByteBuf, Mono<Void>>> ff) {
    this.service = service;
    this.marshaller = marshaller;
    this.unmarshaller = unmarshaller;
    this.rr = rr;
    this.rc = rc;
    this.rs = rs;
    this.ff = ff;
  }

  @Override
  public String getService() {
    return service;
  }

  @Override
  public Mono<Void> fireAndForget(Payload payload) {
    try {
      ByteBuf data = payload.sliceData();
      ByteBuf metadata = payload.sliceMetadata();

      ByteBuf buf = Metadata.getMetadata(metadata);
      String method = Metadata.getMethod(metadata);

      BiFunction<Object, ByteBuf, Mono<Void>> ff = this.ff.get(method);

      if (ff == null) {
        return Mono.error(
            new NullPointerException("nothing found for service " + service + " method " + method));
      }

      Object input = unmarshaller.apply(data);

      return ff.apply(input, buf);

    } catch (Throwable t) {
      return Mono.error(t);
    } finally {
      payload.release();
    }
  }

  @Override
  public Mono<Payload> requestResponse(Payload payload) {
    try {
      ByteBuf data = payload.sliceData();
      ByteBuf metadata = payload.sliceMetadata();

      ByteBuf buf = Metadata.getMetadata(metadata);
      String method = Metadata.getMethod(metadata);
      BiFunction<Object, ByteBuf, Mono> rr = this.rr.get(method);

      if (rr == null) {
        return Mono.error(
            new NullPointerException("nothing found for service " + service + " method " + method));
      }

      Object input = unmarshaller.apply(data);

      return rr.apply(input, buf).map(this::marshall);

    } catch (Throwable t) {
      return Mono.error(t);
    } finally {
      payload.release();
    }
  }

  @Override
  public Flux<Payload> requestStream(Payload payload) {
    try {
      ByteBuf data = payload.sliceData();
      ByteBuf metadata = payload.sliceMetadata();

      ByteBuf buf = Metadata.getMetadata(metadata);
      String method = Metadata.getMethod(metadata);

      Object input = unmarshaller.apply(data);
      BiFunction<Object, ByteBuf, Flux> rs = this.rs.get(method);

      if (rs == null) {
        return Flux.error(
            new NullPointerException("nothing found for service " + service + " method " + method));
      }

      return rs.apply(input, buf).map(this::marshall);

    } catch (Throwable t) {
      return Flux.error(t);
    } finally {
      payload.release();
    }
  }

  @Override
  public Flux<Payload> requestChannel(Payload payload, Flux<Payload> publisher) {
    try {
      ByteBuf data = payload.sliceData();
      ByteBuf metadata = payload.sliceMetadata();

      ByteBuf buf = Metadata.getMetadata(metadata);
      String method = Metadata.getMethod(metadata);

      Object input = unmarshaller.apply(data);
      TriFunction<Object, Publisher, ByteBuf, Flux> rc = this.rc.get(method);

      if (rc == null) {
        return Flux.error(
            new NullPointerException("nothing found for service " + service + " method " + method));
      }

      Flux f =
          publisher.map(
              p -> {
                try {
                  Object o = unmarshaller.apply(p.sliceData());
                  return o;
                } finally {
                  p.release();
                }
              });

      return rc.apply(input, f, buf).map(this::marshall);

    } catch (Throwable t) {
      return Flux.error(t);
    }
  }

  private Payload marshall(Object o) {
    ByteBuf data = marshaller.apply(o);
    return ByteBufPayload.create(data);
  }
}
