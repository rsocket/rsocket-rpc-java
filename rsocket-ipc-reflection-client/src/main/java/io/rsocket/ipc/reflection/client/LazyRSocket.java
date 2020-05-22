package io.rsocket.ipc.reflection.client;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.ResponderRSocket;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class LazyRSocket implements ResponderRSocket {

	public static LazyRSocket create(Mono<RSocket> rsocketMono) {
		Objects.requireNonNull(rsocketMono);
		AtomicReference<CompletableFuture<RSocket>> futureRef = new AtomicReference<>(new CompletableFuture<>());
		rsocketMono.subscribe(v -> {
			if (!futureRef.get().isDone()) {
				synchronized (futureRef) {
					if (!futureRef.get().isDone()) {
						futureRef.get().complete(v);
						return;
					}
				}
			}
			futureRef.set(CompletableFuture.completedFuture(v));
		});
		Supplier<RSocket> rSocketSupplier = () -> {
			try {
				return futureRef.get().get();
			} catch (InterruptedException | ExecutionException e) {
				throw java.lang.RuntimeException.class.isAssignableFrom(e.getClass())
						? java.lang.RuntimeException.class.cast(e)
						: new java.lang.RuntimeException(e);
			}
		};
		return create(rSocketSupplier);
	}

	public static LazyRSocket create(Supplier<RSocket> rsocketSupplier) {
		Objects.requireNonNull(rsocketSupplier);
		return new LazyRSocket() {

			@Override
			protected RSocket getRSocket() {
				return rsocketSupplier.get();
			}
		};
	}

	protected abstract RSocket getRSocket();

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		return getRSocket().fireAndForget(payload);
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		return getRSocket().requestResponse(payload);
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		return getRSocket().requestStream(payload);
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return getRSocket().requestChannel(payloads);
	}

	@Override
	public Flux<Payload> requestChannel(Payload payload, Publisher<Payload> payloads) {
		RSocket rSocket = getRSocket();
		if (rSocket instanceof ResponderRSocket)
			return ((ResponderRSocket) rSocket).requestChannel(payload, payloads);
		return rSocket.requestChannel(payloads);
	}

	@Override
	public Mono<Void> metadataPush(Payload payload) {
		return getRSocket().metadataPush(payload);
	}

	@Override
	public double availability() {
		return getRSocket().availability();
	}

	@Override
	public Mono<Void> onClose() {
		return getRSocket().onClose();
	}

	@Override
	public void dispose() {
		getRSocket().dispose();
	}

	@Override
	public boolean isDisposed() {
		return getRSocket().isDisposed();
	}

}
