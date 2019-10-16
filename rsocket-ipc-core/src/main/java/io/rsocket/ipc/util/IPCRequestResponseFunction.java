package io.rsocket.ipc.util;

import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.rsocket.Payload;
import io.rsocket.ipc.Functions;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Unmarshaller;
import io.rsocket.util.ByteBufPayload;
import reactor.core.publisher.Mono;

public class IPCRequestResponseFunction implements IPCFunction<Mono<Payload>> {

    final String route;
    final Unmarshaller unmarshaller;
    final Marshaller marshaller;
    final Functions.RequestResponse rr;

    public IPCRequestResponseFunction(String route, Unmarshaller unmarshaller, Marshaller marshaller, Functions.RequestResponse rr) {
        this.route = route;
        this.unmarshaller = unmarshaller;
        this.marshaller = marshaller;
        this.rr = rr;
    }

    @Override
    public Mono<Payload> apply(ByteBuf data, ByteBuf metadata, SpanContext context) {
        Object input = unmarshaller.apply(data);
        return rr
                .apply(input, metadata)
                .map(o -> ByteBufPayload.create(marshaller.apply(o)));
    }
}
