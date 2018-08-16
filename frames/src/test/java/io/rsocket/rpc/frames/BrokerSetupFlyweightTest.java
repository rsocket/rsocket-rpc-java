package io.rsocket.rpc.frames;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

public class BrokerSetupFlyweightTest {
  @Test
  public void testEncoding() {
    ByteBuf authToken = Unpooled.wrappedBuffer("access token".getBytes());

    ByteBuf byteBuf =
        BrokerSetupFlyweight.encode(
            ByteBufAllocator.DEFAULT, "brokerId", "clusterId", Long.MAX_VALUE, authToken);
    
    Assert.assertEquals("brokerId", BrokerSetupFlyweight.brokerId(byteBuf));
    Assert.assertEquals("clusterId", BrokerSetupFlyweight.clusterId(byteBuf));
    Assert.assertEquals(Long.MAX_VALUE, BrokerSetupFlyweight.accessKey(byteBuf));
    authToken.resetReaderIndex();
    Assert.assertTrue(ByteBufUtil.equals(authToken,  BrokerSetupFlyweight.accessToken(byteBuf)));
  }
}
