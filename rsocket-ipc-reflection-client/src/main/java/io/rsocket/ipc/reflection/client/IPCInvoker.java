package io.rsocket.ipc.reflection.client;

import io.rsocket.RSocket;

public interface IPCInvoker {

	public Object invoke(RSocket rSocket, Object[] args) throws Throwable;
}
