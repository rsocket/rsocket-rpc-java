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
import io.rsocket.ipc.util.TriFunction;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class Server {
  Server() {}

  public interface P {
    <I> U<I> marshall(Marshaller<I> marshaller);
  }

  public interface U<I> {
    <O> H<I, O> unmarshall(Unmarshaller<O> unmarshaller);
  }

  public interface H<I, O> {
    H<I, O> requestResponse(String route, Functions.RequestResponse<I, O> rr);

    H<I, O> requestChannel(String route, Functions.HandleRequestHandle<I, O> rc);

    H<I, O> requestStream(String route, Functions.RequestStream<I, O> rs);

    H<I, O> fireAndForget(String route, Functions.FireAndForget<I> ff);

    IPCRSocket rsocket();
  }

  @SuppressWarnings("unchecked")
  private static class Builder implements P, U, H {
    private final String service;
    private Marshaller marshaller;
    private Unmarshaller unmarshaller;
    private final Map<String, BiFunction<Object, ByteBuf, Mono>> rr;
    private final Map<String, TriFunction<Object, Publisher, ByteBuf, Flux>> rc;
    private final Map<String, BiFunction<Object, ByteBuf, Flux>> rs;
    private final Map<String, BiFunction<Object, ByteBuf, Mono<Void>>> ff;

    private Builder(String service) {
      this.rr = new HashMap<>();
      this.rc = new HashMap<>();
      this.rs = new HashMap<>();
      this.ff = new HashMap<>();
      this.service = service;
    }

    @Override
    public <I> U<I> marshall(Marshaller<I> marshaller) {
      Objects.requireNonNull(marshaller);
      this.marshaller = marshaller;
      return this;
    }

    @Override
    public H unmarshall(Unmarshaller unmarshaller) {
      Objects.requireNonNull(unmarshaller);
      this.unmarshaller = unmarshaller;
      return this;
    }

    @Override
    public H requestResponse(String route, Functions.RequestResponse rr) {
      Objects.requireNonNull(rr);
      this.rr.put(route, rr);
      return this;
    }

    @Override
    public H requestChannel(String route, Functions.HandleRequestHandle rc) {
      Objects.requireNonNull(rc);
      this.rc.put(route, rc);
      return this;
    }

    @Override
    public H requestStream(String route, Functions.RequestStream rs) {
      Objects.requireNonNull(rs);
      this.rs.put(route, rs);
      return this;
    }

    @Override
    public H fireAndForget(String route, Functions.FireAndForget ff) {
      Objects.requireNonNull(ff);
      this.ff.put(route, ff);
      return this;
    }

    @Override
    public IPCRSocket rsocket() {
      Objects.requireNonNull(marshaller);
      Objects.requireNonNull(unmarshaller);
      return new IPCServerRSocket(service, marshaller, unmarshaller, rr, rc, rs, ff);
    }
  }

  public static P service(String service) {
    Objects.requireNonNull(service);
    return new Builder(service);
  }
}
