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
import io.rsocket.ipc.util.TriFunction;
import io.rsocket.rpc.frames.Metadata;
import io.rsocket.rpc.metrics.Metrics;
import io.rsocket.rpc.tracing.Tag;
import io.rsocket.rpc.tracing.Tracing;
import io.rsocket.util.ByteBufPayload;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SuppressWarnings("unchecked")
class IPCServerRSocket extends AbstractRSocket implements IPCRSocket {
  private final String service;
  private final Marshaller marshaller;
  private final Unmarshaller unmarshaller;
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

  private final Map<String, BiFunction<Object, ByteBuf, Mono>> rr;
  private final Map<String, TriFunction<Object, Publisher, ByteBuf, Flux>> rc;
  private final Map<String, BiFunction<Object, ByteBuf, Flux>> rs;
  private final Map<String, BiFunction<Object, ByteBuf, Mono<Void>>> ff;

  IPCServerRSocket(
      String service,
      Marshaller marshaller,
      Unmarshaller unmarshaller,
      Map<String, BiFunction<Object, ByteBuf, Mono>> rr,
      Map<String, TriFunction<Object, Publisher, ByteBuf, Flux>> rc,
      Map<String, BiFunction<Object, ByteBuf, Flux>> rs,
      Map<String, BiFunction<Object, ByteBuf, Mono<Void>>> ff,
      MeterRegistry meterRegistry,
      Tracer tracer) {
    this.service = service;
    this.marshaller = marshaller;
    this.unmarshaller = unmarshaller;
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

      BiFunction<Object, ByteBuf, Mono<Void>> ff = this.ff.get(method);

      if (ff == null) {
        return Mono.error(
            new NullPointerException("nothing found for service " + service + " method " + method));
      }

      Object input = unmarshaller.apply(data);

      SpanContext spanContext = Tracing.deserializeTracingMetadata(tracer, metadata);

      return ff.apply(input, buf)
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
      BiFunction<Object, ByteBuf, Mono> rr = this.rr.get(method);

      if (rr == null) {
        return Mono.error(
            new NullPointerException("nothing found for service " + service + " method " + method));
      }

      Object input = unmarshaller.apply(data);

      SpanContext spanContext = Tracing.deserializeTracingMetadata(tracer, metadata);

      return rr.apply(input, buf)
          .map(this::marshall)
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

      Object input = unmarshaller.apply(data);
      BiFunction<Object, ByteBuf, Flux> rs = this.rs.get(method);

      if (rs == null) {
        return Flux.error(
            new NullPointerException("nothing found for service " + service + " method " + method));
      }

      SpanContext spanContext = Tracing.deserializeTracingMetadata(tracer, metadata);

      return rs.apply(input, buf)
          .map(this::marshall)
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

      SpanContext spanContext = Tracing.deserializeTracingMetadata(tracer, metadata);

      return rc.apply(input, f, buf)
          .map(this::marshall)
          .transform(getMetric(method))
          .transform(getTracer(method).apply(spanContext));

    } catch (Throwable t) {
      return Flux.error(t);
    }
  }

  private Payload marshall(Object o) {
    ByteBuf data = marshaller.apply(o);
    return ByteBufPayload.create(data);
  }
}
