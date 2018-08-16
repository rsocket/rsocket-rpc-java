package io.rsocket.rpc.frames;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.rsocket.util.NumberUtils;

import java.nio.charset.StandardCharsets;

public class Metadata {
  // Version
  public static final short VERSION = 1;
  
  public static ByteBuf encode(
      ByteBufAllocator allocator,
      String service,
      String method,
      ByteBuf metadata) {
    return encode(allocator, service, method, Unpooled.EMPTY_BUFFER, metadata);
  }
  
  public static ByteBuf encode(
      ByteBufAllocator allocator,
      String service,
      String method,
      ByteBuf tracing,
      ByteBuf metadata) {
    ByteBuf byteBuf = allocator.buffer().writeShort(VERSION);

    int serviceLength = NumberUtils.requireUnsignedShort(ByteBufUtil.utf8Bytes(service));
    byteBuf.writeShort(serviceLength);
    ByteBufUtil.reserveAndWriteUtf8(byteBuf, service, serviceLength);

    int methodLength = NumberUtils.requireUnsignedShort(ByteBufUtil.utf8Bytes(method));
    byteBuf.writeShort(methodLength);
    ByteBufUtil.reserveAndWriteUtf8(byteBuf, method, methodLength);

    byteBuf.writeShort(tracing.readableBytes());
    byteBuf.writeBytes(tracing);

    byteBuf.writeBytes(metadata, metadata.readerIndex(), metadata.readableBytes());

    return byteBuf;
  }

  public static int getVersion(ByteBuf byteBuf) {
    return byteBuf.getShort(0) & 0x7FFF;
  }

  public static String getService(ByteBuf byteBuf) {
    int offset = Short.BYTES;

    int serviceLength = byteBuf.getShort(offset);
    offset += Short.BYTES;

    return byteBuf.toString(offset, serviceLength, StandardCharsets.UTF_8);
  }

  public static String getMethod(ByteBuf byteBuf) {
    int offset = Short.BYTES;

    int serviceLength = byteBuf.getShort(offset);
    offset += Short.BYTES + serviceLength;

    int methodLength = byteBuf.getShort(offset);
    offset += Short.BYTES;

    return byteBuf.toString(offset, methodLength, StandardCharsets.UTF_8);
  }

  public static ByteBuf getTracing(ByteBuf byteBuf) {
    int offset = Short.BYTES;

    int serviceLength = byteBuf.getShort(offset);
    offset += Short.BYTES + serviceLength;

    int methodLength = byteBuf.getShort(offset);
    offset += Short.BYTES + methodLength;

    int tracingLength = byteBuf.getShort(offset);
    offset += Short.BYTES;

    return tracingLength > 0 ? byteBuf.slice(offset, tracingLength) : Unpooled.EMPTY_BUFFER;
  }

  public static ByteBuf getMetadata(ByteBuf byteBuf) {
    int offset = Short.BYTES;

    int serviceLength = byteBuf.getShort(offset);
    offset += Short.BYTES + serviceLength;

    int methodLength = byteBuf.getShort(offset);
    offset += Short.BYTES + methodLength;

    int tracingLength = byteBuf.getShort(offset);
    offset += Short.BYTES + tracingLength;

    int metadataLength = byteBuf.readableBytes() - offset;
    return metadataLength > 0 ? byteBuf.slice(offset, metadataLength) : Unpooled.EMPTY_BUFFER;
  }
}
