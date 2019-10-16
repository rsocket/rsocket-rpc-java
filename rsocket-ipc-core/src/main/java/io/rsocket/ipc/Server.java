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
import io.opentracing.Tracer;
import io.rsocket.Payload;
import io.rsocket.ipc.util.IPCChannelFunction;
import io.rsocket.ipc.util.IPCFireAndForgetFunction;
import io.rsocket.ipc.util.IPCFunction;
import io.rsocket.ipc.util.IPCMetricsAwareFireAndForgetFunction;
import io.rsocket.ipc.util.IPCMetricsAwareRequestChannelFunction;
import io.rsocket.ipc.util.IPCMetricsAwareRequestResponseFunction;
import io.rsocket.ipc.util.IPCMetricsAwareRequestStreamFunction;
import io.rsocket.ipc.util.IPCRequestChannelFunction;
import io.rsocket.ipc.util.IPCRequestResponseFunction;
import io.rsocket.ipc.util.IPCRequestStreamFunction;
import io.rsocket.ipc.util.IPCTracingAndMetricsAwareFireAndForgetFunction;
import io.rsocket.ipc.util.IPCTracingAndMetricsAwareRequestChannelFunction;
import io.rsocket.ipc.util.IPCTracingAndMetricsAwareRequestResponseFunction;
import io.rsocket.ipc.util.IPCTracingAndMetricsAwareRequestStreamFunction;
import io.rsocket.ipc.util.IPCTracingAwareFireAndForgetFunction;
import io.rsocket.ipc.util.IPCTracingAwareRequestChannelFunction;
import io.rsocket.ipc.util.IPCTracingAwareRequestResponseFunction;
import io.rsocket.ipc.util.IPCTracingAwareRequestStreamFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("unchecked")
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

  @SuppressWarnings("unchecked")
  private static class Builder implements P, U, H, M, T {
    private final String service;
    private Marshaller marshaller;
    private MeterRegistry meterRegistry;
    private Tracer tracer;
    private Unmarshaller unmarshaller;

    private final Map<String, IPCFunction<Mono<Void>>> fireAndForgetRegistry;
    private final Map<String, IPCFunction<Mono<Payload>>> requestResponseRegistry;
    private final Map<String, IPCFunction<Flux<Payload>>> requestStreamRegistry;
    private final Map<String, IPCChannelFunction> requestChannelRegistry;

    private Builder(String service) {
      this.requestResponseRegistry = new HashMap<>();
      this.requestChannelRegistry = new HashMap<>();
      this.requestStreamRegistry = new HashMap<>();
      this.fireAndForgetRegistry = new HashMap<>();
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

      if (tracer == null && meterRegistry == null) {
        this.fireAndForgetRegistry.put(route, new IPCFireAndForgetFunction(service + "." + route, unmarshaller, marshaller, ff));
      } else if (tracer != null && meterRegistry != null) {
        this.fireAndForgetRegistry.put(route, new IPCTracingAndMetricsAwareFireAndForgetFunction(service + "." + route, unmarshaller, marshaller, ff, tracer, meterRegistry));
      } else if (tracer != null) {
        this.fireAndForgetRegistry.put(route, new IPCTracingAwareFireAndForgetFunction(service + "." + route, unmarshaller, marshaller, ff, tracer));
      } else {
        this.fireAndForgetRegistry.put(route, new IPCMetricsAwareFireAndForgetFunction(service + "." + route, unmarshaller, marshaller, ff, meterRegistry));
      }

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

      if (tracer == null && meterRegistry == null) {
        this.requestResponseRegistry.put(route, new IPCRequestResponseFunction(service + "." + route, unmarshaller, marshaller, rr));
      } else if (tracer != null && meterRegistry != null) {
        this.requestResponseRegistry.put(route, new IPCTracingAndMetricsAwareRequestResponseFunction(service + "." + route, unmarshaller, marshaller, rr, tracer, meterRegistry));
      } else if (tracer != null) {
        this.requestResponseRegistry.put(route, new IPCTracingAwareRequestResponseFunction(service + "." + route, unmarshaller, marshaller, rr, tracer));
      } else {
        this.requestResponseRegistry.put(route, new IPCMetricsAwareRequestResponseFunction(service + "." + route, unmarshaller, marshaller, rr, meterRegistry));
      }

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
      if (tracer == null && meterRegistry == null) {
        this.requestChannelRegistry.put(route, new IPCRequestChannelFunction(service + "." + route, unmarshaller, marshaller, rc));
      } else if (tracer != null && meterRegistry != null) {
        this.requestChannelRegistry.put(route, new IPCTracingAndMetricsAwareRequestChannelFunction(service + "." + route, unmarshaller, marshaller, rc, tracer, meterRegistry));
      } else if (tracer != null) {
        this.requestChannelRegistry.put(route, new IPCTracingAwareRequestChannelFunction(service + "." + route, unmarshaller, marshaller, rc, tracer));
      } else {
        this.requestChannelRegistry.put(route, new IPCMetricsAwareRequestChannelFunction(service + "." + route, unmarshaller, marshaller, rc, meterRegistry));
      }
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
      if (tracer == null && meterRegistry == null) {
        this.requestStreamRegistry.put(route, new IPCRequestStreamFunction(service + "." + route, unmarshaller, marshaller, rs));
      } else if (tracer != null && meterRegistry != null) {
        this.requestStreamRegistry.put(route, new IPCTracingAndMetricsAwareRequestStreamFunction(service + "." + route, unmarshaller, marshaller, rs, tracer, meterRegistry));
      } else if (tracer != null) {
        this.requestStreamRegistry.put(route, new IPCTracingAwareRequestStreamFunction(service + "." + route, unmarshaller, marshaller, rs, tracer));
      } else {
        this.requestStreamRegistry.put(route, new IPCMetricsAwareRequestStreamFunction(service + "." + route, unmarshaller, marshaller, rs, meterRegistry));
      }
      return this;
    }

    @Override
    public IPCRSocket rsocket() {
      return new IPCServerRSocket(service, fireAndForgetRegistry, requestResponseRegistry, requestStreamRegistry, requestChannelRegistry, meterRegistry, tracer);
    }
  }

  public static M service(String service) {
    Objects.requireNonNull(service);
    return new Builder(service);
  }
}
