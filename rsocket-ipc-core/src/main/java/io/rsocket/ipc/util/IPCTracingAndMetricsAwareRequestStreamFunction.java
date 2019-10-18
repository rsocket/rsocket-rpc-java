package io.rsocket.ipc.util;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.rsocket.Payload;
import io.rsocket.ipc.Functions;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Unmarshaller;
import io.rsocket.ipc.metrics.Metrics;
import io.rsocket.ipc.tracing.Tag;
import io.rsocket.ipc.tracing.Tracing;
import io.rsocket.util.ByteBufPayload;
import reactor.core.publisher.Flux;

public class IPCTracingAndMetricsAwareRequestStreamFunction implements IPCFunction<Flux<Payload>> {

  final String route;
  final Unmarshaller unmarshaller;
  final Marshaller marshaller;
  final Functions.RequestStream rs;
  final Tracer tracer;
  final MeterRegistry meterRegistry;

  public IPCTracingAndMetricsAwareRequestStreamFunction(
      String route,
      Unmarshaller unmarshaller,
      Marshaller marshaller,
      Functions.RequestStream rs,
      Tracer tracer,
      MeterRegistry meterRegistry) {
    this.route = route;
    this.unmarshaller = unmarshaller;
    this.marshaller = marshaller;
    this.rs = rs;
    this.tracer = tracer;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Flux<Payload> apply(ByteBuf data, ByteBuf metadata, SpanContext context) {
    Object input = unmarshaller.apply(data);
    return rs.apply(input, metadata)
        .map(o -> ByteBufPayload.create(marshaller.apply(o)))
        .transform(
            Tracing.traceAsChild(
                    tracer,
                    route,
                    Tag.of("rsocket.route", route),
                    Tag.of("rsocket.ipc.role", "server"),
                    Tag.of("rsocket.ipc.version", "ipc"))
                .apply(context))
        .transform(Metrics.timed(meterRegistry, "rsocket.server", "route", route));
  }
}
