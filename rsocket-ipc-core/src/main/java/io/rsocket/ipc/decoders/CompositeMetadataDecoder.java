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

import static io.rsocket.ipc.frames.Metadata.getMetadata;
import static io.rsocket.ipc.frames.Metadata.getMethod;
import static io.rsocket.ipc.frames.Metadata.getService;
import static io.rsocket.ipc.frames.Metadata.isDecodable;
import static io.rsocket.metadata.CompositeMetadataFlyweight.hasEntry;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.rsocket.Payload;
import io.rsocket.ipc.MetadataDecoder;
import io.rsocket.ipc.tracing.Tracing;
import io.rsocket.metadata.WellKnownMimeType;

public class CompositeMetadataDecoder implements MetadataDecoder {

  static final int STREAM_METADATA_KNOWN_MASK = 0x80; // 1000 0000

  static final byte STREAM_METADATA_LENGTH_MASK = 0x7F; // 0111 1111
  /** Tag max length in bytes */
  static final int TAG_LENGTH_MAX = 0xFF;

  final Tracer tracer;

  public CompositeMetadataDecoder() {
    this(null);
  }

  public CompositeMetadataDecoder(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public Metadata decode(Payload payload) {
    ByteBuf metadata = payload.sliceMetadata();

    // TODO: fix that once Backward compatibility expire
    Metadata compositeMetadata;
    if ((compositeMetadata = resolveCompositeMetadata(metadata)) != null) {
      return compositeMetadata;
    } else if (isDecodable(metadata)) {
      return new DefaultMetadata(metadata, tracer);
    } else {
      // Here we probably got something from Spring-Messaging :D
      return new PlainMetadata(metadata);
    }
  }

  private static final class PlainMetadata implements Metadata {
    private final ByteBuf metadata;

    private PlainMetadata(ByteBuf metadata) {
      this.metadata = metadata;
    }

    @Override
    public ByteBuf metadata() {
      return Unpooled.EMPTY_BUFFER;
    }

    @Override
    public String route() {
      return metadata.toString(CharsetUtil.UTF_8);
    }

    @Override
    public SpanContext spanContext() {
      return null;
    }

    @Override
    public boolean isComposite() {
      return false;
    }
  }

  private static final class DefaultMetadata implements Metadata {

    private final ByteBuf metadata;
    private final Tracer tracer;

    private DefaultMetadata(ByteBuf metadata, Tracer tracer) {
      this.metadata = metadata;
      this.tracer = tracer;
    }

    @Override
    public ByteBuf metadata() {
      return getMetadata(metadata);
    }

    @Override
    public String route() {
      ByteBuf m = this.metadata;
      String service = getService(m);
      String method = getMethod(m);

      return service.isEmpty() ? method : (service + (method.isEmpty() ? "" : ("." + method)));
    }

    @Override
    public SpanContext spanContext() {
      return Tracing.deserializeTracingMetadata(tracer, metadata);
    }

    @Override
    public boolean isComposite() {
      return false;
    }
  }

  private static final class CompositeMetadata implements Metadata {

    private final ByteBuf metadata;
    private final int firstRouteIndex;
    private final int firstRouteLength;

    private CompositeMetadata(ByteBuf metadata, int firstRouteIndex, int firstRouteLength) {
      this.metadata = metadata;
      this.firstRouteIndex = firstRouteIndex;
      this.firstRouteLength = firstRouteLength;
    }

    @Override
    public final ByteBuf metadata() {
      return metadata;
    }

    @Override
    public final String route() {
      int firstRouteIndex = this.firstRouteIndex;
      int firstRouteLength = this.firstRouteLength;

      if (firstRouteIndex < 0 || firstRouteLength < 1) {
        return null;
      }
      return metadata.toString(firstRouteIndex, firstRouteLength, CharsetUtil.UTF_8);
    }

    @Override
    public final SpanContext spanContext() {
      // FIXME: Figure out how to work with tracing metadata
      return null;
    }

    @Override
    public final boolean isComposite() {
      return true;
    }
  }

  /**
   * @param compositeMetadata
   * @return null or {@link CompositeMetadata}
   */
  public static Metadata resolveCompositeMetadata(ByteBuf compositeMetadata) {
    compositeMetadata.markReaderIndex();
    compositeMetadata.readerIndex(0);

    int firstRouteIndex = -1;
    int firstRouteLength = -1;
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
            return null;
          }
        }

        if (compositeMetadata.isReadable(3)) {
          // ensures the length medium can be read
          final int metadataLength = compositeMetadata.readUnsignedMedium();
          if (compositeMetadata.isReadable(metadataLength)) {
            if (mimeIdOrLength < 0
                && WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.getIdentifier()
                    == (mimeIdOrLength & STREAM_METADATA_LENGTH_MASK)) {
              int readerIndex = compositeMetadata.readerIndex();
              int routeLength =
                  resolveFirstRouteLength(compositeMetadata, readerIndex, metadataLength);
              firstRouteIndex = readerIndex + 1;
              firstRouteLength = routeLength;

              // FIXME: so far there is no reason to iterate further. Need to be changed once
              // Tracing Metadata appeared
              break;
            }

            compositeMetadata.skipBytes(metadataLength);
          } else {
            compositeMetadata.resetReaderIndex();
            return null;
          }
        } else {
          compositeMetadata.resetReaderIndex();
          return null;
        }
      }
      ridx = compositeMetadata.readerIndex();
    }

    compositeMetadata.resetReaderIndex();
    return new CompositeMetadata(compositeMetadata, firstRouteIndex, firstRouteLength);
  }

  public static int resolveFirstRouteLength(ByteBuf metadata, int readerIndex, int metadataLength) {
    int tagLength = TAG_LENGTH_MAX & metadata.getByte(readerIndex);

    return tagLength > metadataLength ? -1 : tagLength;
  }
}
