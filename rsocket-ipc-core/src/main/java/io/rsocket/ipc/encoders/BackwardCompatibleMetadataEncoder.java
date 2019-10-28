package io.rsocket.ipc.encoders;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.opentracing.SpanContext;
import io.rsocket.ipc.MetadataEncoder;
import io.rsocket.ipc.frames.Metadata;
import io.rsocket.ipc.tracing.Tracing;
import java.util.HashMap;
import java.util.Map;

public class BackwardCompatibleMetadataEncoder implements MetadataEncoder {

  final ByteBufAllocator allocator;

  public BackwardCompatibleMetadataEncoder(ByteBufAllocator allocator) {
    this.allocator = allocator;
  }

  @Override
  public ByteBuf encode(ByteBuf metadata, SpanContext context, String baseRoute, String... parts) {

    if (parts.length != 1) {
      throw new IllegalArgumentException(
          "Number of parts should be strictly equal to 1 but given [" + parts.length + "]");
    }

    if (context != null) {
      final HashMap<String, String> spanMap = new HashMap<>();
      for (Map.Entry<String, String> item : context.baggageItems()) {
        spanMap.put(item.getKey(), item.getValue());
      }

      ByteBuf tracingMetadata = Tracing.mapToByteBuf(allocator, spanMap);
      return Metadata.encode(allocator, baseRoute, parts[0], tracingMetadata, metadata);
    }

    return Metadata.encode(allocator, baseRoute, parts[0], metadata);
  }
}
