package io.rsocket.rpc.frames;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

public class ShardFlyweightTest {
  @Test
  public void testEncoding() {
    ByteBuf shardKey = Unpooled.wrappedBuffer("access token".getBytes());
    ByteBuf metadata = Unpooled.wrappedBuffer("metadata".getBytes());

    ByteBuf byteBuf =
        ShardFlyweight.encode(
            ByteBufAllocator.DEFAULT,
            "fromDestination",
            "fromGroup",
            "toGroup",
            shardKey,
            metadata);

    Assert.assertEquals("fromDestination", ShardFlyweight.fromDestination(byteBuf));
    Assert.assertEquals("fromGroup", ShardFlyweight.fromGroup(byteBuf));
    Assert.assertEquals("toGroup", ShardFlyweight.toGroup(byteBuf));
    shardKey.resetReaderIndex();
    Assert.assertTrue(ByteBufUtil.equals(shardKey, ShardFlyweight.shardKey(byteBuf)));
    metadata.resetReaderIndex();
    Assert.assertTrue(ByteBufUtil.equals(metadata, ShardFlyweight.metadata(byteBuf)));
  }
}
