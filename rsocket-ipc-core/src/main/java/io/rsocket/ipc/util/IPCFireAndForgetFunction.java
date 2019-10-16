package io.rsocket.ipc.util;

import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.rsocket.ipc.Functions;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Unmarshaller;
import reactor.core.publisher.Mono;

public class IPCFireAndForgetFunction implements IPCFunction<Mono<Void>> {

    final String route;
    final Unmarshaller unmarshaller;
    final Marshaller marshaller;
    final Functions.FireAndForget fnf;

    public IPCFireAndForgetFunction(String route, Unmarshaller unmarshaller, Marshaller marshaller, Functions.FireAndForget fnf) {
        this.route = route;
        this.unmarshaller = unmarshaller;
        this.marshaller = marshaller;
        this.fnf = fnf;
    }

    @Override
    public Mono<Void> apply(ByteBuf data, ByteBuf metadata, SpanContext context) {
        Object input = unmarshaller.apply(data);
        return fnf
                .apply(input, metadata);
    }
}
