package io.rsocket.rpc.frames;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;

public class MetadataTest {
  @Test
  public void testEncodeAndDecodeNoTracingData() {
    byte[] bytes = new byte[20];
    ThreadLocalRandom.current().nextBytes(bytes);
    String service = "foo";
    String method = "bar";
    ByteBuf tracing = Unpooled.EMPTY_BUFFER;
    ByteBuf metadata = Unpooled.wrappedBuffer(Unpooled.wrappedBuffer(bytes));

    ByteBuf encode =
        Metadata.encode(ByteBufAllocator.DEFAULT, service, method, tracing, metadata);

    String method1 = Metadata.getMethod(encode);
    String service1 = Metadata.getService(encode);

    Assert.assertEquals(method, method1);
    Assert.assertEquals(service, service1);

    ByteBuf tracing1 = Metadata.getTracing(encode);
    ByteBuf metadata1 = Metadata.getMetadata(encode);

    Assert.assertEquals(0, tracing1.readableBytes());
    Assert.assertEquals(20, metadata1.readableBytes());
  }

  @Test
  public void testEcodeAndDecodeWithTracingData() {
    byte[] bytes = new byte[20];
    ThreadLocalRandom.current().nextBytes(bytes);
    String service = "foo";
    String method = "bar";

    ByteBuf tracing = Unpooled.wrappedBuffer(Unpooled.wrappedBuffer(bytes));
    ByteBuf metadata = Unpooled.wrappedBuffer(Unpooled.wrappedBuffer(bytes));

    ByteBuf encode =
        Metadata.encode(ByteBufAllocator.DEFAULT, service, method, tracing, metadata);

    String method1 = Metadata.getMethod(encode);
    String service1 = Metadata.getService(encode);

    Assert.assertEquals(method, method1);
    Assert.assertEquals(service, service1);

    ByteBuf tracing1 = Metadata.getTracing(encode);
    ByteBuf metadata1 = Metadata.getMetadata(encode);

    Assert.assertEquals(20, tracing1.readableBytes());
    Assert.assertEquals(20, metadata1.readableBytes());
  }
}
