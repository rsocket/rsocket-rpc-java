package io.rsocket.rpc.util;

import org.junit.Assert;
import org.junit.Test;

public class StringUtilTest {
  @Test
  public void testShouldReturnTrue() {
    boolean l = StringUtil.hasLetters("localhost");
    Assert.assertTrue(l);

    l = StringUtil.hasLetters("foo.com");
    Assert.assertTrue(l);
  }

  @Test
  public void testShouldReturnFalse() {
    boolean l = StringUtil.hasLetters("123.123.123.123");
    Assert.assertFalse(l);
  }
}
