package io.rsocket.ipc.util;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.rsocket.Payload;
import io.rsocket.ipc.Functions;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Unmarshaller;
import io.rsocket.rpc.metrics.Metrics;
import io.rsocket.util.ByteBufPayload;
import reactor.core.publisher.Flux;

public class IPCMetricsAwareRequestStreamFunction implements IPCFunction<Flux<Payload>> {

    final String route;
    final Unmarshaller unmarshaller;
    final Marshaller marshaller;
    final Functions.RequestStream rs;
    final MeterRegistry meterRegistry;

    public IPCMetricsAwareRequestStreamFunction(String route, Unmarshaller unmarshaller, Marshaller marshaller, Functions.RequestStream rs, MeterRegistry meterRegistry) {
        this.route = route;
        this.unmarshaller = unmarshaller;
        this.marshaller = marshaller;
        this.rs = rs;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Flux<Payload> apply(ByteBuf data, ByteBuf metadata, SpanContext context) {
        Object input = unmarshaller.apply(data);
        return rs
                .apply(input, metadata)
                .map(o -> ByteBufPayload.create(marshaller.apply(o)))
                .transform(Metrics.timed(meterRegistry, "rsocket.server", "route", route));
    }
}
