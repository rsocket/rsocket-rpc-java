package io.rsocket.ipc.reflection.server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.reactivestreams.Publisher;

import io.netty.buffer.ByteBuf;
import io.opentracing.Tracer;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.MetadataDecoder;
import io.rsocket.ipc.RequestHandlingRSocket;
import io.rsocket.ipc.Server;
import io.rsocket.ipc.Server.H;
import io.rsocket.ipc.Unmarshaller;
import io.rsocket.ipc.marshallers.Bytes;
import io.rsocket.ipc.reflection.core.MethodMapUtils;
import io.rsocket.ipc.reflection.core.PublisherConverter;
import io.rsocket.ipc.reflection.core.PublisherConverters;
import io.rsocket.ipc.util.IPCUtils;
import io.rsocket.ipc.util.TriFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

public class RequestHandlingRSocketReflection extends RequestHandlingRSocket {
	private static final Class<?> THIS_CLASS = new Object() {
	}.getClass().getEnclosingClass();
	private static final Logger logger = java.util.logging.Logger.getLogger(THIS_CLASS.getName());
	private final Scheduler subscribeOnScheduler;

	public RequestHandlingRSocketReflection(Scheduler subscribeOnScheduler) {
		super();
		this.subscribeOnScheduler = subscribeOnScheduler;
	}

	public RequestHandlingRSocketReflection(Scheduler subscribeOnScheduler, MetadataDecoder decoder) {
		super(decoder);
		this.subscribeOnScheduler = subscribeOnScheduler;
	}

	public RequestHandlingRSocketReflection(Scheduler subscribeOnScheduler, Tracer tracer) {
		super(tracer);
		this.subscribeOnScheduler = subscribeOnScheduler;
	}

	public <S> void register(Class<S> serviceType, S service, Marshaller<Object> resultMarshaller,
			Unmarshaller<Object[]> unmarshaller) {
		register(serviceType, service, resultMarshaller, (paramTypes, bb, md) -> unmarshaller.apply(bb));
	}

	public <S> void register(Class<S> serviceType, S service, Marshaller<Object> resultMarshaller,
			Function<Type[], Unmarshaller<Object[]>> argumentDeserializer) {
		register(serviceType, service, resultMarshaller,
				(paramTypes, bb, md) -> argumentDeserializer.apply(paramTypes).apply(bb));
	}

	public <S> void register(Class<S> serviceType, S service, Marshaller<Object> resultMarshaller,
			TriFunction<Type[], ByteBuf, ByteBuf, Object[]> argumentDeserializer) {
		Objects.requireNonNull(serviceType);
		Objects.requireNonNull(service);
		Objects.requireNonNull(resultMarshaller);
		Objects.requireNonNull(argumentDeserializer);
		Map<String, Method> methods = MethodMapUtils.getMappedMethods(serviceType, true);
		Set<String> serviceNameTracker = new HashSet<>();
		for (boolean lowercase : Arrays.asList(false, true)) {
			for (boolean simpleName : Arrays.asList(false, true)) {
				String serviceName = simpleName ? serviceType.getSimpleName() : serviceType.getName();
				serviceName = lowercase ? serviceName.toLowerCase() : serviceName;
				if (!serviceNameTracker.add(serviceName))
					continue;
				register(service, serviceName, resultMarshaller, argumentDeserializer, methods);
			}
		}
	}

	private <S> void register(S service, String serviceName, Marshaller<Object> resultMarshaller,
			TriFunction<Type[], ByteBuf, ByteBuf, Object[]> argumentDeserializer, Map<String, Method> methodMapping) {
		// wrap the deserializer so that it counts down references
		TriFunction<Type[], ByteBuf, ByteBuf, Object[]> releasingArgumentDeserializer = (types, data, md) -> {
			return IPCUtils.releaseOnFinally(() -> argumentDeserializer.apply(types, data, md), data);
		};
		H<Object, ByteBuf> serviceBuilder = Server.service(serviceName).noMeterRegistry().noTracer()
				.marshall(resultMarshaller).unmarshall(Bytes.byteBufUnmarshaller());
		methodMapping.entrySet().forEach(ent -> {
			String route = ent.getKey();
			logger.log(Level.FINE,
					String.format("registering request handler. serviceName:%s route:%s", serviceName, route));
			register(service, route, releasingArgumentDeserializer, ent.getValue(), serviceBuilder);
		});
		this.withEndpoint(serviceBuilder.toIPCRSocket());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private <S> void register(S service, String route,
			TriFunction<Type[], ByteBuf, ByteBuf, Object[]> argumentDeserializer, Method method,
			H<Object, ByteBuf> serviceBuilder) {
		if (registerRequestChannel(service, route, argumentDeserializer, method, serviceBuilder))
			return;
		if (MethodMapUtils.isFireAndForget(method)) {
			serviceBuilder.fireAndForget(route, (data, md) -> {
				Object[] arguments = argumentDeserializer.apply(method.getGenericParameterTypes(), data, md);
				Mono<Mono<Void>> wrappedMono = asMono(() -> {
					invoke(service, method, arguments);
					return Mono.empty();
				}, this.subscribeOnScheduler);
				return wrappedMono.flatMap(v -> v);
			});
			return;
		}
		Optional<PublisherConverter> returnPublisherConverter = PublisherConverters.lookup(method.getReturnType())
				.map(v -> v);
		if (returnPublisherConverter.isPresent() && !Mono.class.isAssignableFrom(method.getReturnType())) {
			serviceBuilder.requestStream(route, (data, md) -> {
				Object[] arguments = argumentDeserializer.apply(method.getGenericParameterTypes(), data, md);
				return asFlux(() -> {
					Object result = invoke(service, method, arguments);
					return returnPublisherConverter.get().toPublisher(result);
				}, this.subscribeOnScheduler);
			});
			return;
		}
		serviceBuilder.requestResponse(route, (data, md) -> {
			Object[] arguments = argumentDeserializer.apply(method.getGenericParameterTypes(), data, md);
			Mono<Mono<Object>> wrappedMono = asMono(() -> {
				Object result = invoke(service, method, arguments);
				if (returnPublisherConverter.isPresent())
					return Mono.from(returnPublisherConverter.get().toPublisher(result));
				return Mono.just(result);
			}, this.subscribeOnScheduler);
			return wrappedMono.flatMap(v -> v);
		});
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private <S> boolean registerRequestChannel(S service, String route,
			TriFunction<Type[], ByteBuf, ByteBuf, Object[]> argumentDeserializer, Method method,
			H<Object, ByteBuf> serviceBuilder) {
		Optional<Type> requestChannelParameterType = MethodMapUtils.getRequestChannelParameterType(method);
		if (!requestChannelParameterType.isPresent())
			return false;
		PublisherConverter returnPublisherConverter = PublisherConverters.lookup(method.getReturnType()).get();
		Type[] typeArguments = new Type[] { requestChannelParameterType.get() };
		serviceBuilder.requestChannel(route, (first, publisher, md) -> {
			md.retain();
			Runnable mdRelease = () -> {
				if (md.refCnt() > 0)
					md.release();
			};
			return IPCUtils.onError(() -> {
				Flux<Object[]> argArrayPublisher = Flux.from(publisher).map(bb -> {
					Object[] argArray = argumentDeserializer.apply(typeArguments, bb, md);
					return argArray;
				});
				Flux<Object> objPublisher = argArrayPublisher.map(arr -> arr[0]);
				return asFlux(() -> {
					Object result = invoke(service, method, new Object[] { objPublisher });
					return returnPublisherConverter.toPublisher(result);
				}, this.subscribeOnScheduler).doOnTerminate(mdRelease);
			}, mdRelease);
		});
		return true;
	}

	private static <X> Flux<X> asFlux(Supplier<Publisher<X>> supplier, Scheduler scheduler) {
		Supplier<Publisher<X>> memoizedSupplier = memoized(supplier);
		Flux<X> result = Flux.defer(memoizedSupplier);
		if (scheduler != null)
			result = result.subscribeOn(scheduler);
		return result;
	}

	private static <X> Mono<X> asMono(Supplier<X> supplier, Scheduler scheduler) {
		Supplier<X> memoizedSupplier = memoized(supplier);
		Mono<X> result = Mono.fromSupplier(memoizedSupplier);
		if (scheduler != null)
			result = result.subscribeOn(scheduler);
		return result;
	}

	private static <S> Object invoke(S serivce, Method method, Object[] arguments) {
		try {
			return method.invoke(serivce, arguments);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw java.lang.RuntimeException.class.isAssignableFrom(e.getClass())
					? java.lang.RuntimeException.class.cast(e)
					: new java.lang.RuntimeException(e);
		}
	}

	private static <X> Supplier<X> memoized(Supplier<X> supplier) {
		Objects.requireNonNull(supplier);
		AtomicReference<Optional<X>> ref = new AtomicReference<>();
		return () -> {
			if (ref.get() == null)
				synchronized (ref) {
					if (ref.get() == null)
						ref.set(Optional.ofNullable(supplier.get()));
				}
			return ref.get().orElse(null);
		};
	}

}
