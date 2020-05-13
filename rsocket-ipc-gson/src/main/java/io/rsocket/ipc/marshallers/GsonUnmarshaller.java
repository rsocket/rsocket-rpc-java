package io.rsocket.ipc.marshallers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.rsocket.ipc.Unmarshaller;

public class GsonUnmarshaller<X> implements Unmarshaller<X> {

	public static <X> GsonUnmarshaller<X> create(Gson gson, TypeToken<X> typeToken) {
		return create(gson, typeToken == null ? null : typeToken.getType());
	}

	public static <X> GsonUnmarshaller<X> create(Gson gson, Class<X> classType) {
		return create(gson, (Type) classType);
	}

	public static <X> GsonUnmarshaller<X> create(Gson gson, Type type) {
		Objects.requireNonNull(gson);
		Objects.requireNonNull(type);
		return new GsonUnmarshaller<>(isSupplier -> parseUnchecked(gson, type, isSupplier));
	}

	public static GsonUnmarshaller<Object[]> create(Gson gson, Type[] types) {
		Objects.requireNonNull(gson);
		Objects.requireNonNull(types);
		if (types.length == 0)
			throw new IllegalArgumentException("types are required");
		for (Type type : types)
			Objects.requireNonNull(type);
		Function<Supplier<InputStream>, Object[]> parser = isSupplier -> {
			JsonArray jarr = parseUnchecked(gson, JsonArray.class, isSupplier);
			Object[] result = new Object[jarr.size()];
			for (int i = 0; i < jarr.size(); i++) {
				Type type = types == null || types.length <= i ? null : types[i];
				if (type == null)
					type = Object.class;
				result[i] = gson.fromJson(jarr.get(i), type);
			}
			return result;
		};
		return new GsonUnmarshaller<>(parser);
	}

	private final Function<Supplier<InputStream>, X> parser;

	protected GsonUnmarshaller(Function<Supplier<InputStream>, X> parser) {
		this.parser = Objects.requireNonNull(parser);
	}

	@Override
	public X apply(ByteBuf byteBuf) {
		return parser.apply(() -> new ByteBufInputStream(byteBuf));
	}

	public X apply(byte[] byteArray) {
		Objects.requireNonNull(byteArray);
		return parser.apply(() -> new ByteArrayInputStream(byteArray));
	}

	private static <Y> Y parseUnchecked(Gson gson, Type type, Supplier<InputStream> inputStreamSupplier) {
		Objects.requireNonNull(gson);
		Objects.requireNonNull(inputStreamSupplier);
		type = type != null ? type : Object.class;
		try (InputStream is = inputStreamSupplier.get(); InputStreamReader reader = new InputStreamReader(is);) {
			return gson.fromJson(reader, type);
		} catch (IOException e) {
			throw java.lang.RuntimeException.class.isAssignableFrom(e.getClass())
					? java.lang.RuntimeException.class.cast(e)
					: new java.lang.RuntimeException(e);
		} finally {
			// DO NOT RELEASE THE PAYLOAD, I BELIEVE THAT THIS BREAKDS RSOCKET RPC JAVA
			// if (releaseOnParse && byteBuf.refCnt() > 0)
			// byteBuf.release();
		}
	}

}
