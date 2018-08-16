package io.rsocket.rpc.exception;

public class ServiceNotFound extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ServiceNotFound(String service) {
    super("can not find service " + service);
  }
}
