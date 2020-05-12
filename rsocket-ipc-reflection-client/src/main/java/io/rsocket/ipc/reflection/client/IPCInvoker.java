package io.rsocket.ipc.reflection.client;

import io.rsocket.RSocket;
import reactor.core.publisher.Mono;

public interface IPCInvoker {

	public Object invoke(Mono<RSocket> rSocketMono, Object[] args) throws Throwable;
}
