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
package io.rsocket.ipc.util;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentracing.Tracer;
import io.rsocket.Payload;
import io.rsocket.ipc.Functions;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.MetadataDecoder;
import io.rsocket.ipc.Unmarshaller;
import io.rsocket.ipc.metrics.Metrics;
import io.rsocket.ipc.tracing.Tag;
import io.rsocket.ipc.tracing.Tracing;
import io.rsocket.util.ByteBufPayload;
import reactor.core.publisher.Mono;

@SuppressWarnings("rawtypes")
public class IPCTracingAndMetricsAwareRequestResponseFunction
    implements IPCFunction<Mono<Payload>> {

  final String route;
  final Unmarshaller unmarshaller;
  final Marshaller marshaller;
  final Functions.RequestResponse rr;
  final Tracer tracer;
  final MeterRegistry meterRegistry;

  public IPCTracingAndMetricsAwareRequestResponseFunction(
      String route,
      Unmarshaller unmarshaller,
      Marshaller marshaller,
      Functions.RequestResponse rr,
      Tracer tracer,
      MeterRegistry meterRegistry) {
    this.route = route;
    this.unmarshaller = unmarshaller;
    this.marshaller = marshaller;
    this.rr = rr;
    this.tracer = tracer;
    this.meterRegistry = meterRegistry;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Mono<Payload> apply(Payload payload, MetadataDecoder.Metadata metadata) {
    Object input = unmarshaller.apply(payload.sliceData());
    return rr.apply(input, metadata.metadata())
        .map(o -> ByteBufPayload.create(marshaller.apply(o)))
        .transform(
            Tracing.traceAsChild(
                    tracer,
                    route,
                    Tag.of("rsocket.route", route),
                    Tag.of("rsocket.ipc.role", "server"),
                    Tag.of("rsocket.ipc.version", "ipc"))
                .apply(metadata.spanContext()))
        .transform(Metrics.timed(meterRegistry, "rsocket.server", "route", route));
  }
}
