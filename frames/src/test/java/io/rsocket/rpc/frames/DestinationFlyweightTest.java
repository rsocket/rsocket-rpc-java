package io.rsocket.rpc.frames;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

public class DestinationFlyweightTest {
  @Test
  public void testEncoding() {
    ByteBuf metadata = Unpooled.wrappedBuffer("metadata".getBytes());
    ByteBuf byteBuf =
        DestinationFlyweight.encode(
            ByteBufAllocator.DEFAULT,
            "fromDestination",
            "fromGroup",
            "toDestination",
            "toGroup",
            metadata);

    Assert.assertEquals("fromDestination", DestinationFlyweight.fromDestination(byteBuf));
    Assert.assertEquals("fromGroup", DestinationFlyweight.fromGroup(byteBuf));
    Assert.assertEquals("toDestination", DestinationFlyweight.toDestination(byteBuf));
    Assert.assertEquals("toGroup", DestinationFlyweight.toGroup(byteBuf));
    metadata.resetReaderIndex();
    Assert.assertTrue(ByteBufUtil.equals(metadata, DestinationFlyweight.metadata(byteBuf)));
  }
}
