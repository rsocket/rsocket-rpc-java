package io.rsocket.rpc.util;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.Assert;
import org.junit.Test;

public class DnsUntilTest {
  @Test
  public void testShouldReturnIpAddress() {
    InetSocketAddress inetSocketAddress = DnsUntil.toIpAddress("localhost", 8001);
    InetSocketAddress unresolved = InetSocketAddress.createUnresolved("127.0.0.1", 8001);
    Assert.assertTrue(unresolved.equals(inetSocketAddress));
  }

  @Test
  public void testShouldReturnIpAddressFromHostName() {
    InetSocketAddress inetSocketAddress = InetSocketAddress.createUnresolved("localhost", 8001);
    SocketAddress socketAddress = DnsUntil.checkInetSocketAddress(inetSocketAddress);
    InetSocketAddress unresolved = InetSocketAddress.createUnresolved("127.0.0.1", 8001);
    Assert.assertTrue(socketAddress.equals(unresolved));
  }
}
