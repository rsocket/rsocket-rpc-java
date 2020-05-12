package io.rsocket.ipc.reflection;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

import org.reactivestreams.Publisher;

import io.rsocket.ipc.util.IPCUtils;
import reactor.core.publisher.Flux;

@SuppressWarnings("unchecked")
public interface PublisherConverter<X> {

	static PublisherConverter<Publisher<?>> direct() {
		return new Abs<Publisher<?>>() {

			@Override
			protected Publisher<?> toPublisherInternal(Publisher<?> input) {
				return input;
			}

			@Override
			public Publisher<?> fromPublisher(Publisher<?> publisher) {
				return publisher != null ? publisher : Flux.empty();
			}

			@Override
			public Class<? extends Publisher<?>> getConvertType() {
				return (Class<? extends Publisher<?>>) Publisher.class;
			}

			public int priority() {
				return 0;
			}
		};
	}

	static PublisherConverter<Stream<?>> stream() {
		return new Abs<Stream<?>>() {

			@Override
			protected Publisher<?> toPublisherInternal(Stream<?> input) {
				return Flux.fromStream(input);
			}

			@Override
			public Stream<?> fromPublisher(Publisher<?> publisher) {
				if (publisher == null)
					return Stream.empty();
				return Flux.from(publisher).toStream();
			}

			@Override
			public Class<? extends Stream<?>> getConvertType() {
				return (Class<? extends Stream<?>>) Stream.class;
			}

			public int priority() {
				return 1;
			}
		};
	}

	static PublisherConverter<Iterator<?>> iterator() {
		return new Abs<Iterator<?>>() {

			@Override
			protected Publisher<?> toPublisherInternal(Iterator<?> input) {
				return Flux.fromStream(IPCUtils.stream(input));
			}

			@Override
			public Iterator<?> fromPublisher(Publisher<?> publisher) {
				if (publisher == null)
					return Collections.emptyIterator();
				return Flux.from(publisher).toStream().iterator();
			}

			@Override
			public Class<? extends Iterator<?>> getConvertType() {
				return (Class<? extends Iterator<?>>) Iterator.class;
			}

			public int priority() {
				return 2;
			}
		};
	}

	static PublisherConverter<Iterable<?>> iterable() {
		return new Abs<Iterable<?>>() {

			@Override
			protected Publisher<?> toPublisherInternal(Iterable<?> input) {
				return Flux.fromStream(IPCUtils.stream(input.iterator()));
			}

			@Override
			public Iterable<?> fromPublisher(Publisher<?> publisher) {
				if (publisher == null)
					return Collections.emptyList();
				Flux<?> cachedFlux = Flux.from(publisher).cache();
				Iterable<?> ible = () -> {
					Iterator<Object> iter = (Iterator<Object>) cachedFlux.toStream().iterator();
					return iter;
				};
				return ible != null ? ible : Collections.emptyList();
			}

			@Override
			public Class<? extends Iterable<?>> getConvertType() {
				return (Class<? extends Iterable<?>>) Iterable.class;
			}

		};
	}

	Publisher<?> toPublisher(X input);

	X fromPublisher(Publisher<?> publisher);

	Class<? extends X> getConvertType();

	default int priority() {
		return Integer.MAX_VALUE;
	}

	default boolean appliesTo(Class<?> classType) {
		return getConvertType().isAssignableFrom(classType);
	}

	default Optional<Type> getTypeArgument(Type type) {
		if (type == null)
			return Optional.empty();
		if (type instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) type;
			Type[] actualTypeArguments = pt.getActualTypeArguments();
			if (actualTypeArguments != null && actualTypeArguments.length == 1)
				return Optional.of(actualTypeArguments[0]);
		}
		if (!(type instanceof Class))
			return Optional.empty();
		Class<?> superClass = ((Class<?>) type).getSuperclass();
		if (superClass != null && !this.getConvertType().isAssignableFrom(superClass))
			return Optional.empty();
		return getTypeArgument(superClass);
	}

	static abstract class Abs<X> implements PublisherConverter<X> {

		@Override
		public Publisher<?> toPublisher(X input) {
			if (input == null)
				return Flux.empty();
			return toPublisherInternal(input);
		}

		protected abstract Publisher<?> toPublisherInternal(X input);

	}
}
