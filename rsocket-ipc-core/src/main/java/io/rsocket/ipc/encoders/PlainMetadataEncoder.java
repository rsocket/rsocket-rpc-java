package io.rsocket.ipc.encoders;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.opentracing.SpanContext;
import io.rsocket.ipc.MetadataEncoder;
import java.nio.charset.Charset;

public class PlainMetadataEncoder implements MetadataEncoder {

  final CharSequence delimiter;
  private final Charset charset;

  public PlainMetadataEncoder(CharSequence delimiter, Charset charset) {
    this.delimiter = delimiter;
    this.charset = charset;
  }

  @Override
  public ByteBuf encode(ByteBuf metadata, SpanContext context, String baseRoute, String... parts) {
    ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();

    StringBuilder stringBuilder = new StringBuilder();

    stringBuilder.append(baseRoute);

    for (String part : parts) {
      stringBuilder.append(delimiter).append(part);
    }

    buffer.writeCharSequence(stringBuilder, charset);

    return buffer;
  }
}
