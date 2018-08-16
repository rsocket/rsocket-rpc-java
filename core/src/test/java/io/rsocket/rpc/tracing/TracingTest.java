package io.rsocket.rpc.tracing;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class TracingTest {

  @Test
  public void testSerializeAndDeserialize() throws Exception {
    Map<String, String> map = new HashMap<>();

    map.put("one", "1");
    map.put("two", "2");
    map.put("three", "3");
    map.put("four", "4");
    map.put("five", "5");

    ByteBuf byteBuf = Tracing.mapToByteBuf(ByteBufAllocator.DEFAULT, map);

    Map<String, String> byteBufMap = Tracing.byteBufToMap(byteBuf);

    Assert.assertEquals(map.size(), byteBufMap.size());
    Assert.assertEquals(map.get("two"), byteBufMap.get("two"));
    Assert.assertEquals(map.get("four"), byteBufMap.get("four"));
  }
}
