package io.rsocket.rpc.frames;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

public class BroadcastFlyweightTest {

  @Test
  public void testEncoding() {
    ByteBuf metadata = Unpooled.wrappedBuffer("metadata".getBytes());
    ByteBuf byteBuf =
        BroadcastFlyweight.encode(ByteBufAllocator.DEFAULT, "fromDestination", "fromGroup", "toGroup", metadata);

    Assert.assertEquals("fromDestination", BroadcastFlyweight.fromDestination(byteBuf));
    Assert.assertEquals("fromGroup", BroadcastFlyweight.fromGroup(byteBuf));
    Assert.assertEquals("toGroup", BroadcastFlyweight.toGroup(byteBuf));
    metadata.resetReaderIndex();
    System.out.println(ByteBufUtil.prettyHexDump(BroadcastFlyweight.metadata(byteBuf)));
    Assert.assertTrue(
        ByteBufUtil.equals(metadata, BroadcastFlyweight.metadata(byteBuf)));
  }
}
