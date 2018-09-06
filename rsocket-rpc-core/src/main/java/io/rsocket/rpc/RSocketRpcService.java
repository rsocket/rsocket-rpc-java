package io.rsocket.rpc;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import reactor.core.publisher.Flux;

public interface RSocketRpcService extends RSocket {
  String getService();

  Flux<Payload> requestChannel(Payload payload, Flux<Payload> publisher);
}
