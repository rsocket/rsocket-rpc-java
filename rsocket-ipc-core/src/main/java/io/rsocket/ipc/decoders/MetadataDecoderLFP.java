package io.rsocket.ipc.decoders;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.rsocket.Payload;
import io.rsocket.ipc.MetadataDecoder;
import io.rsocket.ipc.encoders.MetadataReader;
import io.rsocket.ipc.mimetype.MimeTypes;
import io.rsocket.ipc.tracing.Tracing;
import io.rsocket.ipc.util.MetadataUtils.DisposableAddList;
import reactor.core.Disposable;

public class MetadataDecoderLFP implements MetadataDecoder {

	public static interface Interceptor extends Consumer<MetadataReader> {
	};

	private final Tracer tracer;
	private final DisposableAddList<MetadataDecoderLFP.Interceptor> interceptors = DisposableAddList.create();

	public MetadataDecoderLFP(MetadataDecoderLFP.Interceptor... interceptors) {
		this((Tracer) null, interceptors);
	}

	public MetadataDecoderLFP(Tracer tracer, MetadataDecoderLFP.Interceptor... interceptors) {
		this.tracer = tracer;
		if (interceptors != null)
			Arrays.asList(interceptors).stream().filter(Objects::nonNull).forEach(v -> this.addInterceptor(v));
	}

	public Disposable addInterceptor(MetadataDecoderLFP.Interceptor interceptor) {
		Objects.requireNonNull(interceptor);
		return interceptors.disposableAdd(interceptor);
	}

	@Override
	public final <RESULT> RESULT decode(Payload payload, Handler<RESULT> transformer) throws Exception {
		ByteBuf metadata = payload.sliceMetadata();
		// i think that we can retain reader slices bc we slice the data from the
		// payload
		MetadataReader metadataReader = new MetadataReader(metadata, true);
		interceptors.forEach(v -> v.accept(metadataReader));
		return decode(payload.sliceData(), metadataReader, metadata, transformer);
	}

	protected <RESULT> RESULT decode(ByteBuf data, MetadataReader metadataReader, ByteBuf metadata,
			Handler<RESULT> transformer) throws Exception {
		String route = getRoute(metadataReader);
		SpanContext context = readTracingSpanContext(metadataReader);
		RESULT result = transformer.handleAndReply(data, metadata, route, context);
		return result;
	}

	private String getRoute(MetadataReader metadataReader) {
		Stream<String> stream = Stream.empty();
		stream = Stream.concat(stream, metadataReader.streamStrings(MimeTypes.MIME_TYPE_SERVICE));
		stream = Stream.concat(stream, metadataReader.streamStrings(MimeTypes.MIME_TYPE_METHOD));
		String route = stream.collect(Collectors.joining("."));
		return route;
	}

	private SpanContext readTracingSpanContext(MetadataReader metadataReader) {
		if (tracer == null)
			return null;
		Map<String, String> tracerMetadata = new LinkedHashMap<>();
		metadataReader.streamEntriesNonEmpty(MimeTypes.MIME_TYPE_TRACER)
				.forEach(ent -> tracerMetadata.computeIfAbsent(ent.getKey(), nil -> ent.getValue()));
		if (tracerMetadata.isEmpty())
			return null;
		return Tracing.deserializeTracingMetadata(tracer, tracerMetadata);
	}

}
