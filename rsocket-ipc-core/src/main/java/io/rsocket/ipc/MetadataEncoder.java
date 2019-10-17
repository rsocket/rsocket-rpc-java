package io.rsocket.ipc;

import io.netty.buffer.ByteBuf;

@FunctionalInterface
public interface MetadataEncoder<T> {

  ByteBuf encode(T t);
}
