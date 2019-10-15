package io.rsocket.ipc.decoders;

import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.rsocket.Payload;
import io.rsocket.ipc.MetadataDecoder;
import io.rsocket.ipc.util.TriFunction;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.metadata.WellKnownMimeType;

import java.util.Iterator;

import static io.rsocket.metadata.CompositeMetadataFlyweight.hasEntry;

public class CompositMetadataDecoder implements MetadataDecoder {


    static final int STREAM_METADATA_KNOWN_MASK = 0x80; // 1000 0000

    static final byte STREAM_METADATA_LENGTH_MASK = 0x7F; // 0111 1111


    @Override
    public <T> T decode(Payload payload, TriFunction<Payload, String, SpanContext, T> transformer) {
        ByteBuf metadata = payload.sliceMetadata();

        String route;
        SpanContext context;

        Iterator<CompositeMetadata.Entry> iterator = new CompositeMetadata(metadata, false).iterator();

        while (iterator.hasNext()) {
            CompositeMetadata.Entry next = iterator.next();

            if (next.getClass() == CompositeMetadata.WellKnownMimeTypeEntry.class) {
                CompositeMetadata.WellKnownMimeTypeEntry wellKnownMimeTypeEntry = (CompositeMetadata.WellKnownMimeTypeEntry) next;
                WellKnownMimeType type = wellKnownMimeTypeEntry.getType();
                if (type == WellKnownMimeType.MESSAGE_RSOCKET_ROUTING) {
                    route = wellKnownMimeTypeEntry.
                }
            }
        }

        return transformer.apply(payload);
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
