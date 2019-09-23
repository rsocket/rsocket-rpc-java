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

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.buffer.ByteBuf;
import io.opentracing.Tracer;
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

  public interface M {
    T noMeterRegistry();

    T meterRegistry(MeterRegistry registry);
  }

  public interface T {
    P noTracer();

    P tracer(Tracer tracer);
  }

  public interface P {
    <I> U<I> marshall(Marshaller<I> marshaller);
  }

  public interface U<I> {
    <O> H<I, O> unmarshall(Unmarshaller<O> unmarshaller);
  }

  public interface H<I, O> {
    H<I, O> requestResponse(String route, Functions.RequestResponse<O, I> rr);

    H<I, O> requestChannel(String route, Functions.HandleRequestHandle<O, I> rc);

    H<I, O> requestStream(String route, Functions.RequestStream<O, I> rs);

    H<I, O> fireAndForget(String route, Functions.FireAndForget<O> ff);

    <X> H<I, O> requestResponse(
        String route, Marshaller<X> marshaller, Functions.RequestResponse<O, X> rr);

    <X> H<I, O> requestChannel(
        String route, Marshaller<X> marshaller, Functions.HandleRequestHandle<O, X> rc);

    <X> H<I, O> requestStream(
        String route, Marshaller<X> marshaller, Functions.RequestStream<O, X> rs);

    <Y> H<I, O> requestResponse(
        String route, Unmarshaller<Y> unmarshaller, Functions.RequestResponse<Y, I> rr);

    <Y> H<I, O> requestChannel(
        String route, Unmarshaller<Y> unmarshaller, Functions.HandleRequestHandle<Y, I> rc);

    <Y> H<I, O> requestStream(
        String route, Unmarshaller<Y> unmarshaller, Functions.RequestStream<Y, I> rs);

    <Y> H<I, O> fireAndForget(
        String route, Unmarshaller<Y> unmarshaller, Functions.FireAndForget<Y> ff);

    <X, Y> H<I, O> requestResponse(
        String route,
        Unmarshaller<Y> unmarshaller,
        Marshaller<X> marshaller,
        Functions.RequestResponse<Y, X> rr);

    <X, Y> H<I, O> requestChannel(
        String route,
        Unmarshaller<Y> unmarshaller,
        Marshaller<X> marshaller,
        Functions.HandleRequestHandle<Y, X> rc);

    <X, Y> H<I, O> requestStream(
        String route,
        Unmarshaller<Y> unmarshaller,
        Marshaller<X> marshaller,
        Functions.RequestStream<Y, X> rs);

    IPCRSocket rsocket();
  }

  static class RRContext {
    final BiFunction<Object, ByteBuf, Mono> rr;
    final Marshaller marshaller;
    final Unmarshaller unmarshaller;

    public RRContext(
        BiFunction<Object, ByteBuf, Mono> rr, Marshaller marshaller, Unmarshaller unmarshaller) {
      this.rr = rr;
      this.marshaller = marshaller;
      this.unmarshaller = unmarshaller;
    }
  }

  static class RCContext {
    final TriFunction<Object, Publisher, ByteBuf, Flux> rc;
    final Marshaller marshaller;
    final Unmarshaller unmarshaller;

    public RCContext(
        TriFunction<Object, Publisher, ByteBuf, Flux> rc,
        Marshaller marshaller,
        Unmarshaller unmarshaller) {
      this.rc = rc;
      this.marshaller = marshaller;
      this.unmarshaller = unmarshaller;
    }
  }

  static class RSContext {
    final BiFunction<Object, ByteBuf, Flux> rs;
    final Marshaller marshaller;
    final Unmarshaller unmarshaller;

    public RSContext(
        BiFunction<Object, ByteBuf, Flux> rs, Marshaller marshaller, Unmarshaller unmarshaller) {
      this.rs = rs;
      this.marshaller = marshaller;
      this.unmarshaller = unmarshaller;
    }
  }

  static class FFContext {
    final BiFunction<Object, ByteBuf, Mono<Void>> ff;
    final Unmarshaller unmarshaller;

    public FFContext(BiFunction<Object, ByteBuf, Mono<Void>> ff, Unmarshaller unmarshaller) {
      this.ff = ff;
      this.unmarshaller = unmarshaller;
    }
  }

  @SuppressWarnings("unchecked")
  private static class Builder implements P, U, H, M, T {
    private final String service;
    private Marshaller marshaller;
    private MeterRegistry meterRegistry;
    private Tracer tracer;
    private Unmarshaller unmarshaller;
    private final Map<String, RRContext> rr;
    private final Map<String, RCContext> rc;
    private final Map<String, RSContext> rs;
    private final Map<String, FFContext> ff;

    private Builder(String service) {
      this.rr = new HashMap<>();
      this.rc = new HashMap<>();
      this.rs = new HashMap<>();
      this.ff = new HashMap<>();
      this.service = service;
    }

    @Override
    public <I> U<I> marshall(Marshaller<I> marshaller) {
      this.marshaller = Objects.requireNonNull(marshaller);
      return this;
    }

    @Override
    public H unmarshall(Unmarshaller unmarshaller) {
      this.unmarshaller = Objects.requireNonNull(unmarshaller);
      return this;
    }

    @Override
    public T noMeterRegistry() {
      return this;
    }

    @Override
    public T meterRegistry(MeterRegistry meterRegistry) {
      this.meterRegistry = meterRegistry;
      return this;
    }

    @Override
    public P noTracer() {
      return this;
    }

    @Override
    public P tracer(Tracer tracer) {
      this.tracer = tracer;
      return this;
    }

    @Override
    public H requestResponse(String route, Functions.RequestResponse rr) {
      return requestResponse(route, unmarshaller, marshaller, rr);
    }

    @Override
    public H requestChannel(String route, Functions.HandleRequestHandle rc) {
      return requestChannel(route, unmarshaller, marshaller, rc);
    }

    @Override
    public H requestStream(String route, Functions.RequestStream rs) {
      return requestStream(route, unmarshaller, marshaller, rs);
    }

    @Override
    public H fireAndForget(String route, Functions.FireAndForget ff) {
      return fireAndForget(route, unmarshaller, ff);
    }

    @Override
    public H requestResponse(String route, Marshaller marshaller, Functions.RequestResponse rr) {
      return requestResponse(route, unmarshaller, marshaller, rr);
    }

    @Override
    public H requestChannel(String route, Marshaller marshaller, Functions.HandleRequestHandle rc) {
      return requestChannel(route, unmarshaller, marshaller, rc);
    }

    @Override
    public H requestStream(String route, Marshaller marshaller, Functions.RequestStream rs) {
      return requestStream(route, unmarshaller, marshaller, rs);
    }

    @Override
    public H requestResponse(
        String route, Unmarshaller unmarshaller, Functions.RequestResponse rr) {
      return requestResponse(route, unmarshaller, marshaller, rr);
    }

    @Override
    public H requestChannel(
        String route, Unmarshaller unmarshaller, Functions.HandleRequestHandle rc) {
      return requestChannel(route, unmarshaller, marshaller, rc);
    }

    @Override
    public H requestStream(String route, Unmarshaller unmarshaller, Functions.RequestStream rs) {
      return requestStream(route, unmarshaller, marshaller, rs);
    }

    @Override
    public H fireAndForget(String route, Unmarshaller unmarshaller, Functions.FireAndForget ff) {
      Objects.requireNonNull(route);
      Objects.requireNonNull(ff);
      Objects.requireNonNull(unmarshaller);
      this.ff.put(route, new FFContext(ff, unmarshaller));
      return this;
    }

    @Override
    public H requestResponse(
        String route,
        Unmarshaller unmarshaller,
        Marshaller marshaller,
        Functions.RequestResponse rr) {
      Objects.requireNonNull(route);
      Objects.requireNonNull(marshaller);
      Objects.requireNonNull(unmarshaller);
      Objects.requireNonNull(rr);
      this.rr.put(route, new RRContext(rr, marshaller, unmarshaller));
      return this;
    }

    @Override
    public H requestChannel(
        String route,
        Unmarshaller unmarshaller,
        Marshaller marshaller,
        Functions.HandleRequestHandle rc) {
      Objects.requireNonNull(route);
      Objects.requireNonNull(marshaller);
      Objects.requireNonNull(unmarshaller);
      Objects.requireNonNull(rc);
      this.rc.put(route, new RCContext(rc, marshaller, unmarshaller));
      return this;
    }

    @Override
    public H requestStream(
        String route,
        Unmarshaller unmarshaller,
        Marshaller marshaller,
        Functions.RequestStream rs) {
      Objects.requireNonNull(route);
      Objects.requireNonNull(marshaller);
      Objects.requireNonNull(unmarshaller);
      Objects.requireNonNull(rs);
      this.rs.put(route, new RSContext(rs, marshaller, unmarshaller));
      return this;
    }

    @Override
    public IPCRSocket rsocket() {
      return new IPCServerRSocket(service, rr, rc, rs, ff, meterRegistry, tracer);
    }
  }

  public static M service(String service) {
    Objects.requireNonNull(service);
    return new Builder(service);
  }
}
