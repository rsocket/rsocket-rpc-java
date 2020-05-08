package io.rsocket.ipc.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.Spliterators.AbstractSpliterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.rsocket.metadata.WellKnownMimeType;
import reactor.core.Disposable;
import reactor.core.Disposables;

public class MetadataUtils {
	public static final Charset CHARSET = StandardCharsets.UTF_8;

	public static <X> Stream<X> stream(Iterator<X> iterator) {
		if (iterator == null)
			return Stream.empty();
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
	}

	public static <X> Stream<X> flatMap(Stream<? extends Stream<X>> streams) {
		if (streams == null)
			return Stream.empty();
		Iterator<? extends Stream<X>> iter = streams.iterator();
		Spliterator<X> spliterator = new AbstractSpliterator<X>(Long.MAX_VALUE, Spliterator.ORDERED) {

			private Iterator<X> currentIterator;

			@Override
			public boolean tryAdvance(Consumer<? super X> action) {
				synchronized (iter) {
					while (true) {
						if (currentIterator != null && currentIterator.hasNext()) {
							action.accept(currentIterator.next());
							return true;
						}
						if (iter.hasNext()) {
							Stream<X> nextStream = iter.next();
							currentIterator = nextStream == null ? null : nextStream.iterator();
						} else
							break;
					}
				}
				return false;
			}

		};
		return StreamSupport.stream(spliterator, false);
	}

	public static String mimeTypeToString(WellKnownMimeType wellKnownMimeType) {
		return wellKnownMimeType == null ? null : wellKnownMimeType.getString();
	}

	public static String byteBufToString(ByteBuf byteBuf) {
		return Objects.requireNonNull(byteBuf).toString(CHARSET);
	}

	public static ByteBuf byteBufFromString(String str) {
		return Unpooled.wrappedBuffer(Objects.requireNonNull(str).getBytes(CHARSET));
	}

	public static boolean equals(String value1, String value2, boolean ignoreCase) {
		if (Objects.equals(value1, value2))
			return true;
		if (!ignoreCase || value1 == null || value2 == null)
			return false;
		return value1.equalsIgnoreCase(value2);
	}

	public static String urlDecode(String str) {
		try {
			return URLDecoder.decode(str, MetadataUtils.CHARSET.name());
		} catch (UnsupportedEncodingException e) {
			throw java.lang.RuntimeException.class.isAssignableFrom(e.getClass())
					? java.lang.RuntimeException.class.cast(e)
					: new java.lang.RuntimeException(e);
		}
	}

	public static String urlEncode(String str) {
		try {
			return URLEncoder.encode(str, MetadataUtils.CHARSET.name());
		} catch (UnsupportedEncodingException e) {
			throw java.lang.RuntimeException.class.isAssignableFrom(e.getClass())
					? java.lang.RuntimeException.class.cast(e)
					: new java.lang.RuntimeException(e);
		}
	}

	public static boolean isNullOrEmpty(String str) {
		return str == null || str.isEmpty();
	}

	public static boolean nonEmpty(String str) {
		return !isNullOrEmpty(str);
	}

	public static <X extends CharSequence> X requireNonEmpty(X charSequence) {
		Objects.requireNonNull(charSequence);
		if (charSequence.length() <= 0)
			throw new IllegalArgumentException();
		return charSequence;
	}

	private static final AtomicReference<Map<String, WellKnownMimeType>> WellKnownMimeType_FROM_STRING_CACHE_REF = new AtomicReference<>();

	public static Optional<WellKnownMimeType> parseWellKnownMimeType(String mimeType) {
		if (MetadataUtils.isNullOrEmpty(mimeType))
			return Optional.empty();
		if (WellKnownMimeType_FROM_STRING_CACHE_REF.get() == null)
			synchronized (WellKnownMimeType_FROM_STRING_CACHE_REF) {
				if (WellKnownMimeType_FROM_STRING_CACHE_REF.get() == null) {
					Map<String, WellKnownMimeType> map = new HashMap<>();
					for (WellKnownMimeType wkmt : WellKnownMimeType.values()) {
						for (String str : Arrays.asList(wkmt.getString(), wkmt.name())) {
							map.put(str, wkmt);
							map.put(str.toUpperCase(), wkmt);
							map.put(str.toLowerCase(), wkmt);
						}
					}
					WellKnownMimeType_FROM_STRING_CACHE_REF.set(map);
				}
			}
		WellKnownMimeType result = null;
		if (result == null)
			result = WellKnownMimeType_FROM_STRING_CACHE_REF.get().get(mimeType);
		if (result == null)
			result = WellKnownMimeType_FROM_STRING_CACHE_REF.get().get(mimeType.toUpperCase());
		if (result == null)
			result = WellKnownMimeType_FROM_STRING_CACHE_REF.get().get(mimeType.toLowerCase());
		return Optional.ofNullable(result);
	}

	public static class DisposableAddList<X> extends CopyOnWriteArrayList<X> {

		private static final long serialVersionUID = 1L;

		public static <XX> DisposableAddList<XX> create() {
			return new DisposableAddList<XX>();
		}

		public Disposable disposableAdd(X value) {
			Disposable disposable = Disposables.composite(() -> {
				this.removeIf(v -> v == value);
			});
			this.add(value);
			return disposable;
		}
	}
}
