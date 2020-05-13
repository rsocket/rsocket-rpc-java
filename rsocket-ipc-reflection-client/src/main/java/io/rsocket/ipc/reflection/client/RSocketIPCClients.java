package io.rsocket.ipc.reflection.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.reactivestreams.Publisher;
import org.reflections8.ReflectionUtils;

import io.netty.buffer.ByteBuf;
import io.rsocket.RSocket;
import io.rsocket.ipc.Client;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.MetadataEncoder;
import io.rsocket.ipc.marshallers.Bytes;
import io.rsocket.ipc.reflection.core.MethodMapUtils;
import io.rsocket.ipc.reflection.core.PublisherConverter;
import io.rsocket.ipc.reflection.core.PublisherConverters;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RSocketIPCClients {
	private static final Class<?> THIS_CLASS = new Object() {
	}.getClass().getEnclosingClass();
	private static final Logger logger = java.util.logging.Logger.getLogger(THIS_CLASS.getName());
	protected static final Duration RSOCKET_SUPPLIER_WARN_DURATION = Duration.ofSeconds(10);

	public static <X> X create(Mono<RSocket> rSocketMono, Class<X> serviceType, MetadataEncoder metadataEncoder,
			Marshaller<Object[]> argumentMarshaller, BiFunction<Type, ByteBuf, Object> returnDeserializer) {
		try {
			return createInternal(rSocketMono, serviceType, metadataEncoder, argumentMarshaller, returnDeserializer);
		} catch (NoSuchMethodException | IllegalArgumentException | InstantiationException | IllegalAccessException
				| InvocationTargetException e) {
			throw java.lang.RuntimeException.class.isAssignableFrom(e.getClass())
					? java.lang.RuntimeException.class.cast(e)
					: new java.lang.RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	protected static <X> X createInternal(Mono<RSocket> rSocketMono, Class<X> serviceType,
			MetadataEncoder metadataEncoder, Marshaller<Object[]> argumentMarshaller,
			BiFunction<Type, ByteBuf, Object> returnDeserializer) throws NoSuchMethodException,
			IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
		Objects.requireNonNull(rSocketMono);
		Objects.requireNonNull(serviceType);
		Objects.requireNonNull(argumentMarshaller);
		Objects.requireNonNull(returnDeserializer);
		Map<String, Method> mappedMethods = MethodMapUtils.getMappedMethods(serviceType, false);
		Map<Method, Optional<IPCInvoker>> ipcInvokerCache = new ConcurrentHashMap<>();
		Supplier<RSocket> rSocketSupplier = asRSocketSupplier(rSocketMono);
		return (X) proxyFactory(serviceType).create(new Class<?>[] {}, new Object[] {}, new MethodHandler() {

			@Override
			public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
				if (thisMethod.isDefault() && proceed != null)
					return proceed.invoke(self, args);
				Optional<IPCInvoker> ipcInvokerOp = ipcInvokerCache.computeIfAbsent(thisMethod, nil -> {
					Entry<String, Method> entry = mappedMethods.entrySet().stream()
							.filter(ent -> MethodMapUtils.compatibleMethods(thisMethod, ent.getValue())).findFirst()
							.orElse(null);
					if (entry == null)
						return Optional.empty();
					return Optional.of(createIPCInvoker(serviceType, entry.getKey(), metadataEncoder,
							argumentMarshaller, returnDeserializer, thisMethod));
				});
				if (!ipcInvokerOp.isPresent())
					throw new NoSuchMethodException(String.format(
							"could not map method in service. serviceType:%s method:%s", serviceType, thisMethod));
				return ipcInvokerOp.get().invoke(rSocketSupplier.get(), args);
			}

		});
	}

	private static Supplier<RSocket> asRSocketSupplier(Mono<RSocket> rSocketMono) {
		AtomicReference<RSocket> rSocketRef = new AtomicReference<>();
		CountDownLatch rSocketLatch = new CountDownLatch(1);
		return new Supplier<RSocket>() {

			@Override
			public RSocket get() {
				if (rSocketRef.get() == null)
					synchronized (rSocketRef) {
						if (rSocketRef.get() == null) {
							rSocketMono.subscribe(rs -> {
								rSocketRef.set(rs);
								if (rSocketLatch.getCount() > 0)
									rSocketLatch.countDown();
							});
							rSocketLatchAwait();
						}
					}
				return rSocketRef.get();
			}

			private void rSocketLatchAwait() {
				Date startAt = new Date();
				boolean success = false;
				while (!success) {
					try {
						success = rSocketLatch.await(RSOCKET_SUPPLIER_WARN_DURATION.toMillis(), TimeUnit.MILLISECONDS);
					} catch (InterruptedException e) {
						throw java.lang.RuntimeException.class.isAssignableFrom(e.getClass())
								? java.lang.RuntimeException.class.cast(e)
								: new java.lang.RuntimeException(e);
					}
					if (!success) {
						Duration elapsed = Duration.ofMillis(new Date().getTime() - startAt.getTime());
						logger.warning(String.format("awaiting rsocket. elapsed:%s", elapsed.toMillis()));
					}
				}
			}
		};
	}

	@SuppressWarnings("unchecked")
	private static <X> IPCInvoker createIPCInvoker(Class<X> serviceType, String route, MetadataEncoder metadataEncoder,
			Marshaller<Object[]> argumentMarshaller, BiFunction<Type, ByteBuf, Object> returnDeserializer,
			Method method) {
		Function<RSocket, Client<Object[], ByteBuf>> clientSupplier = rSocket -> {
			Client<Object[], ByteBuf> client = Client.service(serviceType.getName()).rsocket(rSocket)
					.customMetadataEncoder(metadataEncoder).noMeterRegistry().noTracer().marshall(argumentMarshaller)
					.unmarshall(Bytes.byteBufUnmarshaller());
			return client;
		};
		Optional<PublisherConverter<?>> returnPublisherConverterOp = PublisherConverters.lookup(method.getReturnType());
		if (MethodMapUtils.getRequestChannelParameterType(method).isPresent()) {
			return (rSocket, args) -> {
				Publisher<Object> objPublisher = (Publisher<Object>) args[0];
				Flux<Object[]> argArrayPublisher = Flux.from(objPublisher).map(v -> new Object[] { v });
				Flux<ByteBuf> responsePublisher = clientSupplier.apply(rSocket).requestChannel(route)
						.apply(argArrayPublisher);
				return returnFromResponsePublisher(method, returnDeserializer, returnPublisherConverterOp.get(),
						responsePublisher);
			};
		}
		if (MethodMapUtils.isFireAndForget(method))
			return (rSocket, args) -> {
				Mono<Void> result = clientSupplier.apply(rSocket).fireAndForget(route).apply(args);
				if (Mono.class.isAssignableFrom(method.getReturnType()))
					return result;
				return null;
			};
		if (returnPublisherConverterOp.isPresent() && !Mono.class.isAssignableFrom(method.getReturnType()))
			return (rSocket, args) -> {
				Flux<ByteBuf> responsePublisher = clientSupplier.apply(rSocket).requestStream(route).apply(args);
				return returnFromResponsePublisher(method, returnDeserializer, returnPublisherConverterOp.get(),
						responsePublisher);
			};
		return (rSocket, args) -> {
			Mono<ByteBuf> responsePublisher = clientSupplier.apply(rSocket).requestResponse(route).apply(args);
			if (returnPublisherConverterOp.isPresent())
				return returnFromResponsePublisher(method, returnDeserializer, returnPublisherConverterOp.get(),
						responsePublisher);
			ByteBuf bb = responsePublisher.block();
			return returnDeserializer.apply(method.getReturnType(), bb);
		};
	}

	private static Object returnFromResponsePublisher(Method method,
			BiFunction<Type, ByteBuf, Object> returnDeserializer, PublisherConverter<?> returnPublisherConverter,
			Publisher<ByteBuf> responsePublisher) {
		Type typeArgument = returnPublisherConverter.getPublisherTypeArgument(method.getGenericReturnType())
				.orElse(Object.class);
		Publisher<Object> resultPublisher;
		if (Mono.class.isAssignableFrom(method.getReturnType()))
			resultPublisher = Mono.from(responsePublisher).map(v -> {
				Object deserialized = returnDeserializer.apply(typeArgument, v);
				return deserialized;
			});
		else
			resultPublisher = Flux.from(responsePublisher).map(v -> {
				Object deserialized = returnDeserializer.apply(typeArgument, v);
				return deserialized;
			});
		Object result = returnPublisherConverter.fromPublisher(resultPublisher);
		return result;
	}

	@SuppressWarnings("unchecked")
	private static ProxyFactory proxyFactory(Class<?> classType, Class<?>... additionalInterfaces) {
		ProxyFactory factory = new ProxyFactory();
		List<Class<?>> classTypes = new ArrayList<>();
		if (classType.isInterface())
			classTypes.add(classType);
		else
			factory.setSuperclass(classType);
		classTypes.addAll(ReflectionUtils.getAllSuperTypes(classType));
		ensureInterfaces(factory, classTypes, additionalInterfaces);
		return factory;
	}

	private static void ensureInterfaces(ProxyFactory factory, Iterable<? extends Class<?>> classTypes,
			Class<?>... additionalInterfaces) {
		if (factory == null)
			return;
		List<Class<?>> setInterfaces = new ArrayList<>(2);
		{// current
			Class<?>[] arr = factory.getInterfaces();
			if (arr != null)
				Arrays.asList(arr).forEach(v -> setInterfaces.add(v));
		}
		{// iterable
			if (classTypes != null)
				classTypes.forEach(v -> setInterfaces.add(v));
		}
		{// array
			if (additionalInterfaces != null)
				Arrays.asList(additionalInterfaces).forEach(v -> setInterfaces.add(v));
		}
		Stream<Class<?>> ifaceStream = setInterfaces.stream();
		ifaceStream = ifaceStream.distinct().filter(Objects::nonNull).filter(Class::isInterface).filter(v -> {
			return !javassist.util.proxy.ProxyObject.class.isAssignableFrom(v);
		});
		factory.setInterfaces(ifaceStream.toArray(Class<?>[]::new));
	}

}
