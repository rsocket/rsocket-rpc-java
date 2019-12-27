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
import io.rsocket.ipc.routing.SimpleRouter;
import io.rsocket.ipc.util.IPCFireAndForgetFunction;
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

    IPCRSocket toIPCRSocket();

    SelfRegistrable toSelfRegistrable();
  }

  @SuppressWarnings("unchecked")
  private static class Builder implements P, U, H, M, T {
    private final String service;
    private Marshaller marshaller;
    private MeterRegistry meterRegistry;
    private Tracer tracer;
    private Unmarshaller unmarshaller;

    private final SimpleRouter router;

    private Builder(String service) {
      this.service = service;
      this.router = new SimpleRouter();
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
    public H requestResponse(String method, Functions.RequestResponse rr) {
      return requestResponse(method, unmarshaller, marshaller, rr);
    }

    @Override
    public H requestChannel(String method, Functions.HandleRequestHandle rc) {
      return requestChannel(method, unmarshaller, marshaller, rc);
    }

    @Override
    public H requestStream(String method, Functions.RequestStream rs) {
      return requestStream(method, unmarshaller, marshaller, rs);
    }

    @Override
    public H fireAndForget(String method, Functions.FireAndForget ff) {
      return fireAndForget(method, unmarshaller, ff);
    }

    @Override
    public H requestResponse(String method, Marshaller marshaller, Functions.RequestResponse rr) {
      return requestResponse(method, unmarshaller, marshaller, rr);
    }

    @Override
    public H requestChannel(
        String method, Marshaller marshaller, Functions.HandleRequestHandle rc) {
      return requestChannel(method, unmarshaller, marshaller, rc);
    }

    @Override
    public H requestStream(String method, Marshaller marshaller, Functions.RequestStream rs) {
      return requestStream(method, unmarshaller, marshaller, rs);
    }

    @Override
    public H requestResponse(
        String method, Unmarshaller unmarshaller, Functions.RequestResponse rr) {
      return requestResponse(method, unmarshaller, marshaller, rr);
    }

    @Override
    public H requestChannel(
        String method, Unmarshaller unmarshaller, Functions.HandleRequestHandle rc) {
      return requestChannel(method, unmarshaller, marshaller, rc);
    }

    @Override
    public H requestStream(String method, Unmarshaller unmarshaller, Functions.RequestStream rs) {
      return requestStream(method, unmarshaller, marshaller, rs);
    }

    @Override
    public H fireAndForget(String method, Unmarshaller unmarshaller, Functions.FireAndForget ff) {
      Objects.requireNonNull(method);
      Objects.requireNonNull(ff);
      Objects.requireNonNull(unmarshaller);

      final String route = service + "." + method;
      if (tracer == null && meterRegistry == null) {
        this.router.withFireAndForgetRoute(
            route, new IPCFireAndForgetFunction(route, unmarshaller, marshaller, ff));
      } else if (tracer != null && meterRegistry != null) {
        this.router.withFireAndForgetRoute(
            route,
            new IPCTracingAndMetricsAwareFireAndForgetFunction(
                route, unmarshaller, marshaller, ff, tracer, meterRegistry));
      } else if (tracer != null) {
        this.router.withFireAndForgetRoute(
            route,
            new IPCTracingAwareFireAndForgetFunction(route, unmarshaller, marshaller, ff, tracer));
      } else {
        this.router.withFireAndForgetRoute(
            route,
            new IPCMetricsAwareFireAndForgetFunction(
                route, unmarshaller, marshaller, ff, meterRegistry));
      }

      return this;
    }

    @Override
    public H requestResponse(
        String method,
        Unmarshaller unmarshaller,
        Marshaller marshaller,
        Functions.RequestResponse rr) {
      Objects.requireNonNull(method);
      Objects.requireNonNull(marshaller);
      Objects.requireNonNull(unmarshaller);
      Objects.requireNonNull(rr);

      final String route = service + "." + method;
      if (tracer == null && meterRegistry == null) {
        this.router.withRequestResponseRoute(
            route, new IPCRequestResponseFunction(route, unmarshaller, marshaller, rr));
      } else if (tracer != null && meterRegistry != null) {
        this.router.withRequestResponseRoute(
            route,
            new IPCTracingAndMetricsAwareRequestResponseFunction(
                route, unmarshaller, marshaller, rr, tracer, meterRegistry));
      } else if (tracer != null) {
        this.router.withRequestResponseRoute(
            route,
            new IPCTracingAwareRequestResponseFunction(
                route, unmarshaller, marshaller, rr, tracer));
      } else {
        this.router.withRequestResponseRoute(
            route,
            new IPCMetricsAwareRequestResponseFunction(
                route, unmarshaller, marshaller, rr, meterRegistry));
      }

      return this;
    }

    @Override
    public H requestChannel(
        String method,
        Unmarshaller unmarshaller,
        Marshaller marshaller,
        Functions.HandleRequestHandle rc) {
      Objects.requireNonNull(method);
      Objects.requireNonNull(marshaller);
      Objects.requireNonNull(unmarshaller);
      Objects.requireNonNull(rc);

      final String route = service + "." + method;
      if (tracer == null && meterRegistry == null) {
        this.router.withRequestChannelRoute(
            route, new IPCRequestChannelFunction(route, unmarshaller, marshaller, rc));
      } else if (tracer != null && meterRegistry != null) {
        this.router.withRequestChannelRoute(
            route,
            new IPCTracingAndMetricsAwareRequestChannelFunction(
                route, unmarshaller, marshaller, rc, tracer, meterRegistry));
      } else if (tracer != null) {
        this.router.withRequestChannelRoute(
            route,
            new IPCTracingAwareRequestChannelFunction(route, unmarshaller, marshaller, rc, tracer));
      } else {
        this.router.withRequestChannelRoute(
            route,
            new IPCMetricsAwareRequestChannelFunction(
                route, unmarshaller, marshaller, rc, meterRegistry));
      }
      return this;
    }

    @Override
    public H requestStream(
        String method,
        Unmarshaller unmarshaller,
        Marshaller marshaller,
        Functions.RequestStream rs) {
      Objects.requireNonNull(method);
      Objects.requireNonNull(marshaller);
      Objects.requireNonNull(unmarshaller);
      Objects.requireNonNull(rs);

      final String route = service + "." + method;
      if (tracer == null && meterRegistry == null) {
        this.router.withRequestStreamRoute(
            route, new IPCRequestStreamFunction(route, unmarshaller, marshaller, rs));
      } else if (tracer != null && meterRegistry != null) {
        this.router.withRequestStreamRoute(
            route,
            new IPCTracingAndMetricsAwareRequestStreamFunction(
                route, unmarshaller, marshaller, rs, tracer, meterRegistry));
      } else if (tracer != null) {
        this.router.withRequestStreamRoute(
            route,
            new IPCTracingAwareRequestStreamFunction(route, unmarshaller, marshaller, rs, tracer));
      } else {
        this.router.withRequestStreamRoute(
            route,
            new IPCMetricsAwareRequestStreamFunction(
                route, unmarshaller, marshaller, rs, meterRegistry));
      }
      return this;
    }

    @Override
    public IPCRSocket toIPCRSocket() {
      return new IPCServerRSocket(service, tracer, router);
    }

    @Override
    public SelfRegistrable toSelfRegistrable() {
      return (mutableRouter) -> {
        router.getFireAndForgetRegistry().forEach(mutableRouter::withFireAndForgetRoute);
        router.getRequestResponseRegistry().forEach(mutableRouter::withRequestResponseRoute);
        router.getRequestStreamRegistry().forEach(mutableRouter::withRequestStreamRoute);
        router.getRequestChannelRegistry().forEach(mutableRouter::withRequestChannelRoute);
      };
    }
  }

  public static M service(String service) {
    Objects.requireNonNull(service);
    return new Builder(service);
  }
}
