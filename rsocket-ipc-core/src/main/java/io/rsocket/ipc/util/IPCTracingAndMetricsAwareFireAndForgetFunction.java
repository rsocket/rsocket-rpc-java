package io.rsocket.ipc.util;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.rsocket.ipc.Functions;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Unmarshaller;
import io.rsocket.rpc.metrics.Metrics;
import io.rsocket.rpc.tracing.Tag;
import io.rsocket.rpc.tracing.Tracing;
import io.rsocket.util.ByteBufPayload;
import reactor.core.publisher.Mono;

public class IPCTracingAndMetricsAwareFireAndForgetFunction implements IPCFunction<Mono<Void>> {

  final String route;
  final Unmarshaller unmarshaller;
  final Marshaller marshaller;
  final Functions.FireAndForget fnf;
  final Tracer tracer;
  final MeterRegistry meterRegistry;

  public IPCTracingAndMetricsAwareFireAndForgetFunction(
      String route,
      Unmarshaller unmarshaller,
      Marshaller marshaller,
      Functions.FireAndForget fnf,
      Tracer tracer,
      MeterRegistry meterRegistry) {
    this.route = route;
    this.unmarshaller = unmarshaller;
    this.marshaller = marshaller;
    this.fnf = fnf;
    this.tracer = tracer;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<Void> apply(ByteBuf data, ByteBuf metadata, SpanContext context) {
    Object input = unmarshaller.apply(data);
    return fnf.apply(input, metadata)
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
