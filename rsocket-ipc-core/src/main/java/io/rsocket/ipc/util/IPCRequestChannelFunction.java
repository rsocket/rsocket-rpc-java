package io.rsocket.ipc.util;

import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.rsocket.Payload;
import io.rsocket.ipc.Functions;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Unmarshaller;
import io.rsocket.util.ByteBufPayload;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

public class IPCRequestChannelFunction implements IPCChannelFunction {

    final String route;
    final Unmarshaller unmarshaller;
    final Marshaller marshaller;
    final Functions.HandleRequestHandle rc;

    public IPCRequestChannelFunction(String route, Unmarshaller unmarshaller, Marshaller marshaller, Functions.HandleRequestHandle rc) {
        this.route = route;
        this.unmarshaller = unmarshaller;
        this.marshaller = marshaller;
        this.rc = rc;
    }

    @Override
    public Flux<Payload> apply(Flux<Payload> source, ByteBuf data, ByteBuf metadata, SpanContext context) {
        return rc
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
                .map(o -> ByteBufPayload.create(marshaller.apply(o)));
    }
}
