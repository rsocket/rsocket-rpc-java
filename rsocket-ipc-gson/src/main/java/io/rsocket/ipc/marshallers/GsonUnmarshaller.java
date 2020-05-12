package io.rsocket.ipc.marshallers;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.rsocket.ipc.Unmarshaller;

public class GsonUnmarshaller<X> implements Unmarshaller<X> {

	public static Object[] apply(Gson gson, Type[] types, boolean releaseOnParse, ByteBuf byteBuf) {
		Objects.requireNonNull(types);
		if (types == null || types.length == 0)
			throw new IllegalArgumentException("unmarshall types are required");
		if (types.length == 1)
			return new Object[] { new GsonUnmarshaller<>(gson, types[0], releaseOnParse).apply(byteBuf) };
		JsonArray jarr = new GsonUnmarshaller<>(gson, JsonArray.class, releaseOnParse).apply(byteBuf);
		Object[] result = new Object[jarr.size()];
		for (int i = 0; i < jarr.size(); i++) {
			Type type = types == null || types.length <= i ? null : types[i];
			if (type == null)
				type = Object.class;
			result[i] = gson.fromJson(jarr.get(i), type);
		}
		return result;

	};

	public static Object apply(Gson gson, Type type, boolean releaseOnParse, ByteBuf byteBuf) {
		return new GsonUnmarshaller<>(gson, type, releaseOnParse).apply(byteBuf);
	}

	private Gson gson;
	private Type type;
	private boolean releaseOnParse;

	public GsonUnmarshaller(Gson gson, Class<X> classType, boolean releaseOnParse) {
		this(gson, (Type) classType, releaseOnParse);
	}

	public GsonUnmarshaller(Gson gson, TypeToken<X> typeToken, boolean releaseOnParse) {
		this(gson, typeToken == null ? null : typeToken.getType(), releaseOnParse);
	}

	protected GsonUnmarshaller(Gson gson, Type type, boolean releaseOnParse) {
		this.gson = Objects.requireNonNull(gson);
		this.type = Objects.requireNonNull(type);
		this.releaseOnParse = releaseOnParse;
	}

	@Override
	public X apply(ByteBuf byteBuf) {
		try (InputStream is = new ByteBufInputStream(byteBuf, releaseOnParse);
				InputStreamReader reader = new InputStreamReader(is);) {
			return gson.fromJson(reader, type);
		} catch (IOException e) {
			throw java.lang.RuntimeException.class.isAssignableFrom(e.getClass())
					? java.lang.RuntimeException.class.cast(e)
					: new java.lang.RuntimeException(e);
		}
	}

	public Gson getGson() {
		return gson;
	}

}
