package io.rsocket.ipc.util;

import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.rsocket.Payload;
import reactor.core.publisher.Flux;

public interface IPCChannelFunction {

  Flux<Payload> apply(Flux<Payload> source, ByteBuf data, ByteBuf metadata, SpanContext context);
}
