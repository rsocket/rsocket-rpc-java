package io.rsocket.ipc.reflection.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
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
				return ipcInvokerOp.get().invoke(rSocketMono, args);
			}

		});
	}

	@SuppressWarnings("unchecked")
	private static <X> IPCInvoker createIPCInvoker(Class<X> serviceType, String route, MetadataEncoder metadataEncoder,
			Marshaller<Object[]> argumentMarshaller, BiFunction<Type, ByteBuf, Object> returnDeserializer,
			Method method) {
		Function<Mono<RSocket>, Client<Object[], ByteBuf>> clientSupplier = rSocketMono -> {
			Client<Object[], ByteBuf> client = Client.service(serviceType.getName()).rsocket(rSocketMono.block())
					.customMetadataEncoder(metadataEncoder).noMeterRegistry().noTracer().marshall(argumentMarshaller)
					.unmarshall(Bytes.byteBufUnmarshaller());
			return client;
		};
		Optional<PublisherConverter<?>> returnPublisherConverterOp = PublisherConverters.lookup(method.getReturnType());
		if (MethodMapUtils.getRequestChannelParameterType(method).isPresent()) {
			return (rSocketMono, args) -> {
				Publisher<Object> objPublisher = (Publisher<Object>) args[0];
				Flux<Object[]> argArrayPublisher = Flux.from(objPublisher).map(v -> new Object[] { v });
				Flux<ByteBuf> responsePublisher = clientSupplier.apply(rSocketMono).requestChannel(route)
						.apply(argArrayPublisher);
				return returnFromResponsePublisher(method, returnDeserializer, returnPublisherConverterOp.get(),
						responsePublisher);
			};
		}
		if (MethodMapUtils.isFireAndForget(method))
			return (rSocketMono, args) -> {
				Mono<Void> result = clientSupplier.apply(rSocketMono).fireAndForget(route).apply(args);
				if (Mono.class.isAssignableFrom(method.getReturnType()))
					return result;
				return null;
			};
		if (returnPublisherConverterOp.isPresent() && !Mono.class.isAssignableFrom(method.getReturnType()))
			return (rSocketMono, args) -> {
				Flux<ByteBuf> responsePublisher = clientSupplier.apply(rSocketMono).requestStream(route).apply(args);
				return returnFromResponsePublisher(method, returnDeserializer, returnPublisherConverterOp.get(),
						responsePublisher);
			};
		return (rSocketMono, args) -> {
			Mono<ByteBuf> responsePublisher = clientSupplier.apply(rSocketMono).requestResponse(route).apply(args);
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
