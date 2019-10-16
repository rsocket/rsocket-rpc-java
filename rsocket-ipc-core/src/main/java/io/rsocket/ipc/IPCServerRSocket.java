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
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.ipc.util.IPCChannelFunction;
import io.rsocket.ipc.util.IPCFunction;
import io.rsocket.rpc.metrics.Metrics;
import io.rsocket.rpc.tracing.Tag;
import io.rsocket.rpc.tracing.Tracing;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

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

  private final Map<String, IPCFunction<Mono<Void>>> fireAndForgetRegistry;
  private final Map<String, IPCFunction<Mono<Payload>>> requestResponseRegistry;
  private final Map<String, IPCFunction<Flux<Payload>>> requestStreamRegistry;
  private final Map<String, IPCChannelFunction> requestChannelRegistry;

  IPCServerRSocket(
      String service,
      Map<String, IPCFunction<Mono<Void>>> fireAndForgetRegistry,
      Map<String, IPCFunction<Mono<Payload>>> requestResponseRegistry,
      Map<String, IPCFunction<Flux<Payload>>> requestStreamRegistry,
      Map<String, IPCChannelFunction> requestChannelRegistry,
      MeterRegistry meterRegistry,
      Tracer tracer) {
    this.service = service;
    this.fireAndForgetRegistry = fireAndForgetRegistry;
    this.requestResponseRegistry = requestResponseRegistry;
    this.requestStreamRegistry = requestStreamRegistry;
    this.requestChannelRegistry = requestChannelRegistry;
    this.meterRegistry = meterRegistry;
    this.tracer = tracer;
    this.metrics = new ConcurrentHashMap<>();
    this.tracers = new ConcurrentHashMap<>();
  }

  @Override
  public void selfRegister(Map<String, IPCFunction<Mono<Void>>> fireAndForgetRegistry,
                           Map<String, IPCFunction<Mono<Payload>>> requestResponseRegistry,
                           Map<String, IPCFunction<Flux<Payload>>> requestStreamRegistry,
                           Map<String, IPCChannelFunction> requestChannelRegistry) {
    requestChannelRegistry.putAll(this.requestChannelRegistry);
    requestResponseRegistry.putAll(this.requestResponseRegistry);
    requestStreamRegistry.putAll(this.requestStreamRegistry);
    fireAndForgetRegistry.putAll(this.fireAndForgetRegistry);
  }

  @Override
  public String getService() {
    return service;
  }

  @Override
  public Mono<Void> fireAndForget(Payload payload) {
    return Mono.error(new RuntimeException());
  }

  @Override
  public Mono<Payload> requestResponse(Payload payload) {
    return Mono.error(new RuntimeException());
  }

  @Override
  public Flux<Payload> requestStream(Payload payload) {
    return Flux.error(new RuntimeException());
  }

  @Override
  public Flux<Payload> requestChannel(Payload payload, Flux<Payload> publisher) {
    return Flux.error(new RuntimeException());
  }
}
