package io.rsocket.ipc.marshallers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Unmarsaller;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import reactor.core.Exceptions;

public class Json {
  private final ObjectMapper mapper;

  private Json() {
    mapper = new ObjectMapper();
    mapper.registerModule(new AfterburnerModule());
  }

  private static class Lazy {
    private static Json INSTANCE = new Json();
  }

  public static Json getInstance() {
    return Lazy.INSTANCE;
  }

  @SuppressWarnings("unused")
  public static <T> Marshaller<T> marshaller(Class<T> clazz) {
    return t -> {
      ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer();
      OutputStream bos = new ByteBufOutputStream(byteBuf);
      try {
        Json.getInstance().mapper.writeValue(bos, t);
      } catch (IOException e) {
        throw Exceptions.propagate(e);
      }
      return byteBuf;
    };
  }

  public static <T> Unmarsaller<T> unmarshaller(Class<T> clazz) {
    return byteBuf -> {
      InputStream bis = new ByteBufInputStream(byteBuf);
      try {
        return Json.getInstance().mapper.readValue(bis, clazz);
      } catch (Throwable t) {
        throw Exceptions.propagate(t);
      }
    };
  }
}
