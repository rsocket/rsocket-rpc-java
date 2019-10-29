package io.rsocket.ipc.util;

import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;

public interface IPCFunction<RESULT> {

  RESULT apply(ByteBuf data, ByteBuf metadata, SpanContext context) throws Exception;
}
