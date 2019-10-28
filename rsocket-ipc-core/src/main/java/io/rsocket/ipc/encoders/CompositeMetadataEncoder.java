package io.rsocket.ipc.encoders;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.opentracing.SpanContext;
import io.rsocket.ipc.MetadataEncoder;
import java.util.Objects;

public class CompositeMetadataEncoder implements MetadataEncoder {

  final ByteBufAllocator allocator;

  public CompositeMetadataEncoder() {
    this.allocator = ByteBufAllocator.DEFAULT;
  }

  public CompositeMetadataEncoder(ByteBufAllocator allocator) {
    this.allocator = Objects.requireNonNull(allocator);
  }

  @Override
  public ByteBuf encode(
      ByteBuf metadata, SpanContext spanContext, String baseRoute, String... parts) {
    throw new UnsupportedOperationException("unsupported");

    //        if (!(metadata instanceof CompositeByteBuf)) {
    //            throw new IllegalArgumentException("Users metadata must be of type
    // CompositeByteBuf");
    //        }
    //
    //        final CompositeByteBuf compositeByteBuf = (CompositeByteBuf) metadata;
    //        final ByteBuf route = ByteBufAllocator.DEFAULT.buffer();
    //
    //        return CompositeMetadataFlyweight.encodeAndAddMetadata(compositeByteBuf, allocator,
    // WellKnownMimeType.MESSAGE_RSOCKET_ROUTING, );
  }
}
