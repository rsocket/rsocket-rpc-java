package io.rsocket.rpc.frames;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class DestinationSetupFlyweight {
  public static ByteBuf encode(
      ByteBufAllocator allocator,
      CharSequence destination,
      CharSequence group,
      long accessKey,
      byte[] accessToken) {
    return encode(allocator, destination, group, accessKey, Unpooled.wrappedBuffer(accessToken));
  }

  public static ByteBuf encode(
      ByteBufAllocator allocator,
      CharSequence destination,
      CharSequence group,
      long accessKey,
      ByteBuf accessToken) {
    Objects.requireNonNull(destination);
    Objects.requireNonNull(group);

    ByteBuf byteBuf = FrameHeaderFlyweight.encodeFrameHeader(allocator, FrameType.DESTINATION_SETUP);

    int destinationLength = ByteBufUtil.utf8Bytes(destination);
    byteBuf.writeInt(destinationLength);
    ByteBufUtil.reserveAndWriteUtf8(byteBuf, destination, destinationLength);

    int groupLength = ByteBufUtil.utf8Bytes(group);
    byteBuf.writeInt(groupLength);
    ByteBufUtil.reserveAndWriteUtf8(byteBuf, group, groupLength);

    int accessTokenSize = accessToken.readableBytes();
    byteBuf.writeLong(accessKey)
        .writeInt(accessTokenSize)
        .writeBytes(accessToken, accessToken.readerIndex(), accessTokenSize);

    return byteBuf;
  }

  public static String destination(ByteBuf byteBuf) {
    int offset = FrameHeaderFlyweight.BYTES;

    int destinationLength = byteBuf.getInt(offset);
    offset += Integer.BYTES;

    return byteBuf.toString(offset, destinationLength, StandardCharsets.UTF_8);
  }

  public static String group(ByteBuf byteBuf) {
    int offset = FrameHeaderFlyweight.BYTES;

    int destinationLength = byteBuf.getInt(offset);
    offset += Integer.BYTES + destinationLength;

    int groupLength = byteBuf.getInt(offset);
    offset += Integer.BYTES;

    return byteBuf.toString(offset, groupLength, StandardCharsets.UTF_8);
  }

  public static long accessKey(ByteBuf byteBuf) {
    int offset = FrameHeaderFlyweight.BYTES;

    int destinationLength = byteBuf.getInt(offset);
    offset += Integer.BYTES + destinationLength;

    int groupLength = byteBuf.getInt(offset);
    offset += Integer.BYTES + groupLength;

    return byteBuf.getLong(offset);
  }

  public static ByteBuf accessToken(ByteBuf byteBuf) {
    int offset = FrameHeaderFlyweight.BYTES;

    int destinationLength = byteBuf.getInt(offset);
    offset += Integer.BYTES + destinationLength;

    int groupLength = byteBuf.getInt(offset);
    offset += Integer.BYTES + groupLength + Long.BYTES;

    int accessTokenLength = byteBuf.getInt(offset);
    offset += Integer.BYTES;

    return byteBuf.slice(offset, accessTokenLength);
  }
}
