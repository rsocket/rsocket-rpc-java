package io.rsocket.rpc.frames;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

public class DestinationSetupFlyweightTest {
  @Test
  public void testEncoding() {

    ByteBuf accessToken = Unpooled.wrappedBuffer("access token".getBytes());

    ByteBuf byteBuf =
        DestinationSetupFlyweight.encode(
            ByteBufAllocator.DEFAULT, "destination", "group", Long.MAX_VALUE, accessToken);

    Assert.assertEquals("destination", DestinationSetupFlyweight.destination(byteBuf));
    Assert.assertEquals("group", DestinationSetupFlyweight.group(byteBuf));
    Assert.assertEquals(Long.MAX_VALUE, DestinationSetupFlyweight.accessKey(byteBuf));
    accessToken.resetReaderIndex();
    Assert.assertTrue(
        ByteBufUtil.equals(accessToken, DestinationSetupFlyweight.accessToken(byteBuf)));
  }
}
