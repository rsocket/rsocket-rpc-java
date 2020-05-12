package io.rsocket.ipc.marshallers;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.function.Function;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.rsocket.ipc.Unmarshaller;

public class GsonUnmarshaller<X> implements Unmarshaller<X> {

	public static <X> GsonUnmarshaller<X> create(Gson gson, TypeToken<X> typeToken, boolean releaseOnParse) {
		return create(gson, typeToken == null ? null : typeToken.getType(), releaseOnParse);
	}

	public static <X> GsonUnmarshaller<X> create(Gson gson, Class<X> classType, boolean releaseOnParse) {
		return create(gson, (Type) classType, releaseOnParse);
	}

	public static <X> GsonUnmarshaller<X> create(Gson gson, Type type, boolean releaseOnParse) {
		Objects.requireNonNull(gson);
		Objects.requireNonNull(type);
		return new GsonUnmarshaller<>(bb -> parseUnchecked(gson, type, bb, releaseOnParse));
	}

	public static GsonUnmarshaller<Object[]> create(Gson gson, Type[] types, boolean releaseOnParse) {
		Objects.requireNonNull(gson);
		Objects.requireNonNull(types);
		return new GsonUnmarshaller<>(bb -> {
			JsonArray jarr = parseUnchecked(gson, JsonArray.class, bb, releaseOnParse);
			Object[] result = new Object[jarr.size()];
			for (int i = 0; i < jarr.size(); i++) {
				Type type = types == null || types.length <= i ? null : types[i];
				if (type == null)
					type = Object.class;
				result[i] = gson.fromJson(jarr.get(i), type);
			}
			return result;
		});
	}

	private final Function<ByteBuf, X> parser;

	protected GsonUnmarshaller(Function<ByteBuf, X> parser) {
		this.parser = Objects.requireNonNull(parser);
	}

	@Override
	public X apply(ByteBuf byteBuf) {
		return parser.apply(byteBuf);
	}

	private static <Y> Y parseUnchecked(Gson gson, Type type, ByteBuf byteBuf, boolean releaseOnParse) {
		Objects.requireNonNull(gson);
		Objects.requireNonNull(byteBuf);
		type = type != null ? type : Object.class;
		try (InputStream is = new ByteBufInputStream(byteBuf, releaseOnParse);
				InputStreamReader reader = new InputStreamReader(is);) {
			return gson.fromJson(reader, type);
		} catch (IOException e) {
			throw java.lang.RuntimeException.class.isAssignableFrom(e.getClass())
					? java.lang.RuntimeException.class.cast(e)
					: new java.lang.RuntimeException(e);
		}
	}

}
