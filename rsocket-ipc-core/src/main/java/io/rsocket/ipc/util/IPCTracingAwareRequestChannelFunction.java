package io.rsocket.ipc.util;

import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.rsocket.Payload;
import io.rsocket.ipc.Functions;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Unmarshaller;
import io.rsocket.rpc.tracing.Tag;
import io.rsocket.rpc.tracing.Tracing;
import io.rsocket.util.ByteBufPayload;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

public class IPCTracingAwareRequestChannelFunction implements IPCChannelFunction {

    final String route;
    final Unmarshaller unmarshaller;
    final Marshaller marshaller;
    final Functions.HandleRequestHandle rs;
    final Tracer tracer;

    public IPCTracingAwareRequestChannelFunction(String route, Unmarshaller unmarshaller, Marshaller marshaller, Functions.HandleRequestHandle rs, Tracer tracer) {
        this.route = route;
        this.unmarshaller = unmarshaller;
        this.marshaller = marshaller;
        this.rs = rs;
        this.tracer = tracer;
    }

    @Override
    public Flux<Payload> apply(Flux<Payload> source, ByteBuf data, ByteBuf metadata, SpanContext context) {
        return rs
                .apply(
                        unmarshaller.apply(data),
                        source.map(p -> {
                            try {
                                ByteBuf dd = p.sliceData();
                                Object result = unmarshaller.apply(dd);
                                p.release();
                                return result;
                            } catch (Throwable t) {
                                p.release();
                                throw Exceptions.propagate(t);
                            }
                        }),
                        metadata
                )
                .map(o -> ByteBufPayload.create(marshaller.apply(o)))
                .transform(Tracing.traceAsChild(
                        tracer,
                        route,
                        Tag.of("rsocket.route", route),
                        Tag.of("rsocket.ipc.role", "server"),
                        Tag.of("rsocket.ipc.version", "ipc")).apply(context));
    }
}
