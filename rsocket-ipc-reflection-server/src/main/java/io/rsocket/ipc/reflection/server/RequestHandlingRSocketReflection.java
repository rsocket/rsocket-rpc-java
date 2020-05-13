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
		H<Object, ByteBuf> serviceBuilder = Server.service(serviceName).noMeterRegistry().noTracer()
				.marshall(resultMarshaller).unmarshall(Bytes.byteBufUnmarshaller());
		methodMapping.entrySet().forEach(ent -> {
			String route = ent.getKey();
			logger.log(Level.FINE,
					String.format("registering request handler. serviceName:%s route:%s", serviceName, route));
			register(service, route, argumentDeserializer, ent.getValue(), serviceBuilder);
		});
		this.withEndpoint(serviceBuilder.toIPCRSocket());
	}

	protected <X> Mono<X> toMono(Supplier<X> object) {
		// TODO Auto-generated method stub
		return null;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private <S> void register(S service, String route,
			TriFunction<Type[], ByteBuf, ByteBuf, Object[]> argumentDeserializer, Method method,
			H<Object, ByteBuf> serviceBuilder) {
		if (registerRequestChannel(service, route, argumentDeserializer, method, serviceBuilder))
			return;
		if (MethodMapUtils.isFireAndForget(method)) {
			serviceBuilder.fireAndForget(route, (data, md) -> {
				Mono<Mono<Void>> mono = asMono(() -> {
					invoke(service, method, argumentDeserializer.apply(method.getGenericParameterTypes(), data, md));
					return Mono.empty();
				});
				return mono.flatMap(v -> v);
			});
			return;
		}
		Optional<PublisherConverter> returnPublisherConverter = PublisherConverters.lookup(method.getReturnType())
				.map(v -> v);
		if (returnPublisherConverter.isPresent() && !Mono.class.isAssignableFrom(method.getReturnType())) {
			serviceBuilder.requestStream(route, (data, md) -> {
				return asFlux(() -> {
					Object result = invoke(service, method,
							argumentDeserializer.apply(method.getGenericParameterTypes(), data, md));
					return returnPublisherConverter.get().toPublisher(result);
				});
			});
			return;
		}
		serviceBuilder.requestResponse(route, (data, md) -> {
			Mono<Mono<Object>> wrapped = asMono(() -> {
				Object result = invoke(service, method,
						argumentDeserializer.apply(method.getGenericParameterTypes(), data, md));
				if (returnPublisherConverter.isPresent())
					return Mono.from(returnPublisherConverter.get().toPublisher(result));
				return Mono.just(result);
			});
			Mono<Object> result = wrapped.flatMap(v -> v);
			return result;
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
			return asFlux(() -> {
				Flux<Object[]> argArrayPublisher = Flux.from(publisher)
						.map(bb -> argumentDeserializer.apply(typeArguments, bb, md));
				Flux<Object> objPublisher = argArrayPublisher.map(arr -> arr[0]);
				Object result = invoke(service, method, new Object[] { objPublisher });
				return returnPublisherConverter.toPublisher(result);
			});
		});
		return true;
	}

	protected <X> Flux<X> asFlux(Supplier<Publisher<X>> supplier) {
		Objects.requireNonNull(supplier);
		Flux<X> result = Flux.defer(supplier);
		if (this.subscribeOnScheduler != null)
			result = result.subscribeOn(this.subscribeOnScheduler);
		return result;
	}

	protected <X> Mono<X> asMono(Supplier<X> supplier) {
		Objects.requireNonNull(supplier);
		Mono<X> result = Mono.fromSupplier(supplier);
		if (this.subscribeOnScheduler != null)
			result = result.subscribeOn(this.subscribeOnScheduler);
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

}
