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
