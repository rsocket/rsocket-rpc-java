package io.rsocket.ipc.util;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.rsocket.ipc.Functions;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Unmarshaller;
import io.rsocket.rpc.metrics.Metrics;
import reactor.core.publisher.Mono;

public class IPCMetricsAwareFireAndForgetFunction implements IPCFunction<Mono<Void>> {

  final String route;
  final Unmarshaller unmarshaller;
  final Marshaller marshaller;
  final Functions.FireAndForget fnf;
  final MeterRegistry meterRegistry;

  public IPCMetricsAwareFireAndForgetFunction(
      String route,
      Unmarshaller unmarshaller,
      Marshaller marshaller,
      Functions.FireAndForget fnf,
      MeterRegistry meterRegistry) {
    this.route = route;
    this.unmarshaller = unmarshaller;
    this.marshaller = marshaller;
    this.fnf = fnf;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<Void> apply(ByteBuf data, ByteBuf metadata, SpanContext context) {
    Object input = unmarshaller.apply(data);
    return fnf.apply(input, metadata)
        .transform(Metrics.timed(meterRegistry, "rsocket.server", "route", route));
  }
}
