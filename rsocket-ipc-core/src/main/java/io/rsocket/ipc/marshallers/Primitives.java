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

public final class Primitives {
  private Primitives() {}

  public static Marshaller<Byte> byteMarshaller() {
    return value -> ByteBufAllocator.DEFAULT.buffer().writeByte(value);
  }

  public static Unmarshaller<Byte> byteUnmarshaller() {
    return ByteBuf::readByte;
  }

  public static Marshaller<Short> shortMarshaller() {
    return value -> ByteBufAllocator.DEFAULT.buffer().writeShort(value);
  }

  public static Unmarshaller<Short> shortUnmarshaller() {
    return ByteBuf::readShort;
  }

  public static Marshaller<Integer> intMarshaller() {
    return value -> ByteBufAllocator.DEFAULT.buffer().writeInt(value);
  }

  public static Unmarshaller<Integer> intUnmarshaller() {
    return ByteBuf::readInt;
  }

  public static Marshaller<Character> charMarshaller() {
    return value -> ByteBufAllocator.DEFAULT.buffer().writeChar(value);
  }

  public static Unmarshaller<Character> charUnmarshaller() {
    return ByteBuf::readChar;
  }

  public static Marshaller<Long> longMarshaller() {
    return value -> ByteBufAllocator.DEFAULT.buffer().writeLong(value);
  }

  public static Unmarshaller<Long> longUnmarshaller() {
    return ByteBuf::readLong;
  }

  public static Marshaller<Float> floatMarshaller() {
    return value -> ByteBufAllocator.DEFAULT.buffer().writeFloat(value);
  }

  public static Unmarshaller<Float> floatUnmarshaller() {
    return ByteBuf::readFloat;
  }

  public static Marshaller<Double> doubleMarshaller() {
    return value -> ByteBufAllocator.DEFAULT.buffer().writeDouble(value);
  }

  public static Unmarshaller<Double> doubleUnmarshaller() {
    return ByteBuf::readDouble;
  }
}
