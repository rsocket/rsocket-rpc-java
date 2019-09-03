package io.rsocket.ipc.marshallers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Unmarshaller;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import reactor.core.Exceptions;

public class Cbor {
  private final ObjectMapper mapper;

  private Cbor() {
    CBORFactory f = new CBORFactory();
    mapper = new ObjectMapper(f);
  }

  private static class Lazy {
    private static Cbor INSTANCE = new Cbor();
  }

  public static Cbor getInstance() {
    return Lazy.INSTANCE;
  }

  @SuppressWarnings("unused")
  public static <T> Marshaller<T> marshaller(Class<T> clazz) {
    return t -> {
      ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer();
      OutputStream bos = new ByteBufOutputStream(byteBuf);
      try {
        Cbor.getInstance().mapper.writeValue(bos, t);
      } catch (IOException e) {
        throw Exceptions.propagate(e);
      }
      return byteBuf;
    };
  }

  public static <T> Unmarshaller<T> unmarshaller(Class<T> clazz) {
    return byteBuf -> {
      InputStream bis = new ByteBufInputStream(byteBuf);
      try {
        return Cbor.getInstance().mapper.readValue(bis, clazz);
      } catch (Throwable t) {
        throw Exceptions.propagate(t);
      }
    };
  }
}
