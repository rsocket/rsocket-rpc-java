package io.rsocket.ipc;

import io.rsocket.Payload;
import io.rsocket.ipc.util.IPCChannelFunction;
import io.rsocket.ipc.util.IPCFunction;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SelfRegistrable {

  void selfRegister(
      Map<String, IPCFunction<Mono<Void>>> fireAndForgetRegistry,
      Map<String, IPCFunction<Mono<Payload>>> requestResponseRegistry,
      Map<String, IPCFunction<Flux<Payload>>> requestStreamRegistry,
      Map<String, IPCChannelFunction> requestChannelRegistry);
}
