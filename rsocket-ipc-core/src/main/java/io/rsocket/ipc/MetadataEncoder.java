package io.rsocket.ipc;

import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;

@FunctionalInterface
public interface MetadataEncoder {

  ByteBuf encode(SpanContext spanContext, String baseRoute, String... parts);
}
