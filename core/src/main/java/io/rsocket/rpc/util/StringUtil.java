package io.rsocket.rpc.util;

public final class StringUtil {
  private StringUtil() {}

  public static boolean hasLetters(String host) {
    int length = host.length();
    for (int i = 0; i < length; i++) {
      char c = host.charAt(i);
      if (Character.isLetter(c)) {
        return true;
      }
    }

    return false;
  }
}
