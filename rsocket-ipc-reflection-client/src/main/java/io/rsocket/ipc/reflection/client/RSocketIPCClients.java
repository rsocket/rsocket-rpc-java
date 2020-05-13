package io.rsocket.ipc.reflection.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
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
		Client<Object[], ByteBuf> client = Client.service(serviceType.getName())
				.rsocket(LazyRSocket.create(rSocketMono)).customMetadataEncoder(metadataEncoder).noMeterRegistry()
				.noTracer().marshall(argumentMarshaller).unmarshall(Bytes.byteBufUnmarshaller());
		Map<Method, Optional<IPCInvoker>> ipcInvokerCache = new ConcurrentHashMap<>();
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
					return Optional.of(createIPCInvoker(client, serviceType, entry.getKey(), metadataEncoder,
							argumentMarshaller, returnDeserializer, thisMethod));
				});
				if (!ipcInvokerOp.isPresent())
					throw new NoSuchMethodException(String.format(
							"could not map method in service. serviceType:%s method:%s", serviceType, thisMethod));
				return ipcInvokerOp.get().invoke(args);
			}

		});
	}

	@SuppressWarnings("unchecked")
	private static <X> IPCInvoker createIPCInvoker(Client<Object[], ByteBuf> client, Class<X> serviceType, String route,
			MetadataEncoder metadataEncoder, Marshaller<Object[]> argumentMarshaller,
			BiFunction<Type, ByteBuf, Object> returnDeserializer, Method method) {
		Optional<PublisherConverter<?>> returnPublisherConverterOp = PublisherConverters.lookup(method.getReturnType());
		if (MethodMapUtils.getRequestChannelParameterType(method).isPresent()) {
			return (args) -> {
				Publisher<Object> objPublisher = (Publisher<Object>) args[0];
				Flux<Object[]> argArrayPublisher = Flux.from(objPublisher).map(v -> new Object[] { v });
				Flux<ByteBuf> responsePublisher = client.requestChannel(route).apply(argArrayPublisher);
				return returnFromResponsePublisher(method, returnDeserializer, returnPublisherConverterOp.get(),
						responsePublisher);
			};
		}
		if (MethodMapUtils.isFireAndForget(method))
			return (args) -> {
				Mono<Void> result = client.fireAndForget(route).apply(args);
				if (Mono.class.isAssignableFrom(method.getReturnType()))
					return result;
				return null;
			};
		if (returnPublisherConverterOp.isPresent() && !Mono.class.isAssignableFrom(method.getReturnType()))
			return (args) -> {
				Flux<ByteBuf> responsePublisher = client.requestStream(route).apply(args);
				return returnFromResponsePublisher(method, returnDeserializer, returnPublisherConverterOp.get(),
						responsePublisher);
			};
		return (args) -> {
			Mono<ByteBuf> responsePublisher = client.requestResponse(route).apply(args);
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
