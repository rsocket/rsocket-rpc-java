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
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.rpc.frames.Metadata;
import io.rsocket.rpc.metrics.Metrics;
import io.rsocket.rpc.tracing.Tag;
import io.rsocket.rpc.tracing.Tracing;
import io.rsocket.util.ByteBufPayload;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SuppressWarnings("unchecked")
class IPCServerRSocket extends AbstractRSocket implements IPCRSocket {
  private final String service;
  private final MeterRegistry meterRegistry;
  private final Tracer tracer;

  private final Map<String, Function<? super Publisher<Payload>, ? extends Publisher<Payload>>>
      metrics;

  private final Map<
          String,
          Function<SpanContext, Function<? super Publisher<Payload>, ? extends Publisher<Payload>>>>
      tracers;

  private Function<? super Publisher<Payload>, ? extends Publisher<Payload>> getMetric(
      String method) {
    return metrics.computeIfAbsent(
        method,
        __ ->
            meterRegistry == null
                ? Function.identity()
                : Metrics.timed(
                    meterRegistry, "rsocket.server", "service", service, "method", method));
  }

  private Function<SpanContext, Function<? super Publisher<Payload>, ? extends Publisher<Payload>>>
      getTracer(String method) {
    return tracers.computeIfAbsent(
        method,
        __ ->
            tracer == null
                ? Tracing.traceAsChild()
                : Tracing.traceAsChild(
                    tracer,
                    method,
                    Tag.of("rsocket.service", service),
                    Tag.of("rsocket.rpc.role", "server"),
                    Tag.of("rsocket.rpc.version", "ipc")));
  }

  private final Map<String, Server.RRContext> rr;
  private final Map<String, Server.RCContext> rc;
  private final Map<String, Server.RSContext> rs;
  private final Map<String, Server.FFContext> ff;

  IPCServerRSocket(
      String service,
      Map<String, Server.RRContext> rr,
      Map<String, Server.RCContext> rc,
      Map<String, Server.RSContext> rs,
      Map<String, Server.FFContext> ff,
      MeterRegistry meterRegistry,
      Tracer tracer) {
    this.service = service;
    this.rr = rr;
    this.rc = rc;
    this.rs = rs;
    this.ff = ff;
    this.meterRegistry = meterRegistry;
    this.tracer = tracer;
    this.metrics = new ConcurrentHashMap<>();
    this.tracers = new ConcurrentHashMap<>();
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

      Server.FFContext ffContext = this.ff.get(method);

      if (ffContext == null) {
        return Mono.error(
            new NullPointerException("nothing found for service " + service + " method " + method));
      }

      Object input = ffContext.unmarshaller.apply(data);

      SpanContext spanContext = Tracing.deserializeTracingMetadata(tracer, metadata);

      return ffContext
          .ff
          .apply(input, buf)
          .transform(
              new Function<Mono<Void>, Publisher<Void>>() {
                @Override
                public Publisher<Void> apply(Mono<Void> voidMono) {
                  Function<? super Publisher<Payload>, ? extends Publisher<Payload>> function =
                      getMetric(method);
                  return Mono.from(function.apply(voidMono.cast(Payload.class))).then();
                }
              })
          .transform(
              new Function<Mono<Void>, Publisher<Void>>() {
                @Override
                public Publisher<Void> apply(Mono<Void> voidMono) {
                  Function<
                          SpanContext,
                          Function<? super Publisher<Payload>, ? extends Publisher<Payload>>>
                      f1 = getTracer(method);
                  Function<? super Publisher<Payload>, ? extends Publisher<Payload>> f2 =
                      f1.apply(spanContext);
                  return Mono.from(f2.apply(voidMono.cast(Payload.class))).then();
                }
              });

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
      Server.RRContext rrContext = this.rr.get(method);

      if (rrContext == null) {
        return Mono.error(
            new NullPointerException("nothing found for service " + service + " method " + method));
      }

      Object input = rrContext.unmarshaller.apply(data);

      SpanContext spanContext = Tracing.deserializeTracingMetadata(tracer, metadata);

      return rrContext
          .rr
          .apply(input, buf)
          .map(o -> marshall(o, rrContext.marshaller))
          .transform(getMetric(method))
          .transform(getTracer(method).apply(spanContext));
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

      Server.RSContext rsContext = this.rs.get(method);

      if (rsContext == null) {
        return Flux.error(
            new NullPointerException("nothing found for service " + service + " method " + method));
      }

      Object input = rsContext.unmarshaller.apply(data);
      SpanContext spanContext = Tracing.deserializeTracingMetadata(tracer, metadata);

      return rsContext
          .rs
          .apply(input, buf)
          .map(o -> marshall(o, rsContext.marshaller))
          .transform(getMetric(method))
          .transform(getTracer(method).apply(spanContext));

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

      Server.RCContext rcContext = this.rc.get(method);

      if (rcContext == null) {
        return Flux.error(
            new NullPointerException("nothing found for service " + service + " method " + method));
      }

      Object input = rcContext.unmarshaller.apply(data);
      Flux f =
          publisher.map(
              p -> {
                try {
                  Object o = rcContext.unmarshaller.apply(p.sliceData());
                  return o;
                } finally {
                  p.release();
                }
              });

      SpanContext spanContext = Tracing.deserializeTracingMetadata(tracer, metadata);

      return rcContext
          .rc
          .apply(input, f, buf)
          .map(o -> marshall(o, rcContext.marshaller))
          .transform(getMetric(method))
          .transform(getTracer(method).apply(spanContext));

    } catch (Throwable t) {
      return Flux.error(t);
    }
  }

  private Payload marshall(Object o, Marshaller marshaller) {
    ByteBuf data = marshaller.apply(o);
    return ByteBufPayload.create(data);
  }
}
