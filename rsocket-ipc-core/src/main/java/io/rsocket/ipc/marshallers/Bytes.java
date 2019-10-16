/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.rsocket.ipc.marshallers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Unmarshaller;
import java.nio.ByteBuffer;

public final class Bytes {
  private Bytes() {}

  public static Marshaller<ByteBuffer> byteBufferMarshaller() {
    return value -> ByteBufAllocator.DEFAULT.buffer().writeBytes(value);
  }

  public static Unmarshaller<ByteBuffer> byteBufferUnmarshaller() {
    return ByteBuf::nioBuffer;
  }

  public static Marshaller<byte[]> byteArrayMarshaller() {
    return value -> ByteBufAllocator.DEFAULT.buffer().writeBytes(value);
  }

  public static Unmarshaller<byte[]> byteArrayUnmarshaller() {
    return value -> {
      byte[] b = new byte[value.readableBytes()];
      value.writeBytes(b);
      return b;
    };
  }

  public static Marshaller<ByteBuf> byteBufMarshaller() {
    return byteBuf -> byteBuf;
  }

  public static Unmarshaller<ByteBuf> byteBufUnmarshaller() {
    return byteBuf -> byteBuf;
  }
}
