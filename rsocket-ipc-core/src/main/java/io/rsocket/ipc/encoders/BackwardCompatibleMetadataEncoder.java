package io.rsocket.ipc.encoders;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.opentracing.SpanContext;
import io.rsocket.ipc.MetadataEncoder;
import io.rsocket.ipc.frames.Metadata;
import io.rsocket.ipc.tracing.Tracing;
import java.util.HashMap;
import java.util.Map;

public class BackwardCompatibleMetadataEncoder implements MetadataEncoder {

  @Override
  public ByteBuf encode(SpanContext context, String baseRoute, String... parts) {

    if (parts.length != 1) {
      throw new IllegalArgumentException(
          "Number of parts should be strictly equal to 1 but given [" + parts.length + "]");
    }

    if (context != null) {
      final HashMap<String, String> spanMap = new HashMap<>();
      for (Map.Entry<String, String> item : context.baggageItems()) {
        spanMap.put(item.getKey(), item.getValue());
      }

      ByteBuf tracingMetadata = Tracing.mapToByteBuf(ByteBufAllocator.DEFAULT, spanMap);
      return Metadata.encode(
          ByteBufAllocator.DEFAULT, baseRoute, parts[0], tracingMetadata, Unpooled.EMPTY_BUFFER);
    }

    return Metadata.encode(ByteBufAllocator.DEFAULT, baseRoute, parts[0], Unpooled.EMPTY_BUFFER);
  }
}
