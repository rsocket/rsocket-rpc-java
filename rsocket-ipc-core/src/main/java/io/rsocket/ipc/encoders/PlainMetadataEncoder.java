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
