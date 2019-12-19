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
