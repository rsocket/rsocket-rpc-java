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
package io.rsocket.ipc;

import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.rsocket.Payload;

@FunctionalInterface
public interface MetadataDecoder {

  Metadata decode(Payload payload) throws Exception;

  final class Metadata {
    public final ByteBuf metadata;
    public final String route;
    public final SpanContext spanContext;
    public final boolean isComposite;

    public Metadata(ByteBuf metadata, String route, SpanContext spanContext, boolean isComposite) {
      this.metadata = metadata;
      this.route = route;
      this.spanContext = spanContext;
      this.isComposite = isComposite;
    }
  }
}
