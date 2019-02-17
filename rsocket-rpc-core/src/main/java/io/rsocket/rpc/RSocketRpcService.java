package io.rsocket.rpc;

import io.rsocket.Payload;
import io.rsocket.ResponderRSocket;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

public interface RSocketRpcService extends ResponderRSocket {
  String getService();

  Flux<Payload> requestChannel(Payload payload, Flux<Payload> publisher);

  default Flux<Payload> requestChannel(Payload payload, Publisher<Payload> payloads) {
    return requestChannel(payload, Flux.from(payloads));
  }
}
