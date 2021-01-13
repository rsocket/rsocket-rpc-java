package io.rsocket.rpc;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.ipc.IPCRSocket;
import reactor.core.publisher.Flux;

public abstract class AbstractRSocketService implements RSocket, IPCRSocket {
  @Override
  public String getService() {
    return getClass().getName();
  }

  @Override
  public Flux<Payload> requestChannel(Payload payload, Flux<Payload> publisher) {
    return Flux.error(new UnsupportedOperationException("Request-Channel not implemented."));
  }

  @Override
  public double availability() {
    return 1.0;
  }

  public abstract Class<?> getServiceClass();
}
