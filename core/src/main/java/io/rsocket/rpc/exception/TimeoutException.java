package io.rsocket.rpc.exception;

public final class TimeoutException extends Exception {

  public static final TimeoutException INSTANCE = new TimeoutException();

  private static final long serialVersionUID = -3094321310317812063L;

  private TimeoutException() {}
}
