package io.rsocket.ipc.util;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.rsocket.Payload;
import io.rsocket.ipc.Functions;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Unmarshaller;
import io.rsocket.ipc.metrics.Metrics;
import io.rsocket.util.ByteBufPayload;
import reactor.core.publisher.Mono;

public class IPCMetricsAwareRequestResponseFunction implements IPCFunction<Mono<Payload>> {

  final String route;
  final Unmarshaller unmarshaller;
  final Marshaller marshaller;
  final Functions.RequestResponse rr;
  final MeterRegistry meterRegistry;

  public IPCMetricsAwareRequestResponseFunction(
      String route,
      Unmarshaller unmarshaller,
      Marshaller marshaller,
      Functions.RequestResponse rr,
      MeterRegistry meterRegistry) {
    this.route = route;
    this.unmarshaller = unmarshaller;
    this.marshaller = marshaller;
    this.rr = rr;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<Payload> apply(ByteBuf data, ByteBuf metadata, SpanContext context) {
    Object input = unmarshaller.apply(data);
    return rr.apply(input, metadata)
        .map(o -> ByteBufPayload.create(marshaller.apply(o)))
        .transform(Metrics.timed(meterRegistry, "rsocket.server", "route", route));
  }
}
