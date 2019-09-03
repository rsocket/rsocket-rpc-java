package io.rsocket.ipc.marshallers;

import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Unmarsaller;
import java.util.function.Function;
import reactor.core.Exceptions;

public class Protobuf {
  @SuppressWarnings("unused")
  public static <T extends Message> Marshaller<T> marshaller(Class<T> clazz) {
    return t -> {
      ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
      ByteBufOutputStream bos = new ByteBufOutputStream(buffer);
      try {
        t.writeTo(bos);
      } catch (Exception e) {
        throw Exceptions.propagate(e);
      }
      return buffer;
    };
  }

  public static <T extends Message> Unmarsaller<T> unmarshaller(Function<ByteBuf, T> f) {
    return f::apply;
  }
}
