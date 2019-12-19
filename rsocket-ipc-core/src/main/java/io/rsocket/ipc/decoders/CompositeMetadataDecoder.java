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
package io.rsocket.ipc.decoders;

import static io.rsocket.metadata.CompositeMetadataFlyweight.hasEntry;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.rsocket.Payload;
import io.rsocket.ipc.MetadataDecoder;
import io.rsocket.ipc.frames.Metadata;
import io.rsocket.ipc.tracing.Tracing;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.metadata.WellKnownMimeType;
import java.nio.charset.Charset;
import java.util.Iterator;

public class CompositeMetadataDecoder implements MetadataDecoder {

  static final int STREAM_METADATA_KNOWN_MASK = 0x80; // 1000 0000

  static final byte STREAM_METADATA_LENGTH_MASK = 0x7F; // 0111 1111

  final Tracer tracer;

  public CompositeMetadataDecoder() {
    this(null);
  }

  public CompositeMetadataDecoder(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public <T> T decode(Payload payload, Handler<T> transformer) throws Exception {
    ByteBuf metadata = payload.sliceMetadata();

    ByteBuf meta;
    String route = null;
    SpanContext context = null;

    // TODO: fix that once Backward compatibility expire
    if (isCompositeMetadata(metadata)) {
      meta = metadata;
      Iterator<CompositeMetadata.Entry> iterator =
          new CompositeMetadata(metadata, false).iterator();

      main:
      while (iterator.hasNext()) {
        CompositeMetadata.Entry next = iterator.next();

        if (next.getClass() == CompositeMetadata.WellKnownMimeTypeEntry.class) {
          CompositeMetadata.WellKnownMimeTypeEntry wellKnownMimeTypeEntry =
              (CompositeMetadata.WellKnownMimeTypeEntry) next;
          WellKnownMimeType type = wellKnownMimeTypeEntry.getType();
          switch (type) {
            case MESSAGE_RSOCKET_ROUTING:
              {
                route = wellKnownMimeTypeEntry.getMimeType();
                // FIXME: once figure out tracing
                break main;
              }
            case MESSAGE_RSOCKET_TRACING_ZIPKIN:
              {
                // TODO: figure out how to decode
              }
          }
        }
      }
    } else {
      try {
        String service = Metadata.getService(metadata);
        String method = Metadata.getMethod(metadata);
        meta = Metadata.getMetadata(metadata);

        route = service + "." + method;
        context = Tracing.deserializeTracingMetadata(tracer, metadata);
      } catch (Throwable t) {
        // Here we probably got something from Spring-Messaging :D
        route = metadata.toString(Charset.defaultCharset());
        meta = Unpooled.EMPTY_BUFFER;
      }
    }

    return transformer.handleAndReply(payload.sliceData(), meta, route, context);
  }

  public static boolean isCompositeMetadata(ByteBuf compositeMetadata) {
    compositeMetadata.markReaderIndex();
    compositeMetadata.readerIndex(0);

    int ridx = 0;
    while (hasEntry(compositeMetadata, ridx)) {
      if (compositeMetadata.isReadable()) {
        byte mimeIdOrLength = compositeMetadata.readByte();
        if ((mimeIdOrLength & STREAM_METADATA_KNOWN_MASK) == STREAM_METADATA_KNOWN_MASK) {
          // noop
        } else {
          // M flag unset, remaining 7 bits are the length of the mime
          int mimeLength = Byte.toUnsignedInt(mimeIdOrLength) + 1;

          if (compositeMetadata.isReadable(
              mimeLength)) { // need to be able to read an extra mimeLength bytes
            // here we need a way for the returned ByteBuf to differentiate between a
            // 1-byte length mime type and a 1 byte encoded mime id, preferably without
            // re-applying the byte mask. The easiest way is to include the initial byte
            // and have further decoding ignore the first byte. 1 byte buffer == id, 2+ byte
            // buffer == full mime string.
            compositeMetadata.skipBytes(mimeLength);
          } else {
            compositeMetadata.resetReaderIndex();
            return false;
          }
        }

        if (compositeMetadata.isReadable(3)) {
          // ensures the length medium can be read
          final int metadataLength = compositeMetadata.readUnsignedMedium();
          if (compositeMetadata.isReadable(metadataLength)) {
            compositeMetadata.skipBytes(metadataLength);
          } else {
            compositeMetadata.resetReaderIndex();
            return false;
          }
        } else {
          compositeMetadata.resetReaderIndex();
          return false;
        }
      }
      ridx = compositeMetadata.readerIndex();
    }

    compositeMetadata.resetReaderIndex();
    return true;
  }
}
