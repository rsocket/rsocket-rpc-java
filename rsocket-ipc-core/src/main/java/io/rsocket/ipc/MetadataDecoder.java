package io.rsocket.ipc;

import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.rsocket.Payload;

@FunctionalInterface
public interface MetadataDecoder {

    <RESULT> RESULT decode(Payload payload, Handler<RESULT> transformer);

    interface Handler<RESULT> {
        RESULT handleAndReply(ByteBuf data, ByteBuf metadata, String route, SpanContext spanContext);
    }
}
