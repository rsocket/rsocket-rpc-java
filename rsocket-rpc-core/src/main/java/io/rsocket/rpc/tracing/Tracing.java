package io.rsocket.rpc.tracing;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.rsocket.rpc.frames.Metadata;
import io.rsocket.util.NumberUtils;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Operators;

public class Tracing {
  private Tracing() {}

  public static SpanContext deserializeTracingMetadata(Tracer tracer, ByteBuf metadata) {
    if (tracer == null) {
      return null;
    }

    ByteBuf tracing = Metadata.getTracing(metadata);

    if (tracing.readableBytes() < 0) {
      return null;
    }

    Map<String, String> metadataMap = byteBufToMap(tracing);
    if (metadataMap.isEmpty()) {
      return null;
    }

    return deserializeTracingMetadata(tracer, metadataMap);
  }

  public static SpanContext deserializeTracingMetadata(
      Tracer tracer, Map<String, String> metadata) {
    TextMapExtractAdapter adapter = new TextMapExtractAdapter(metadata);
    return tracer.extract(Format.Builtin.TEXT_MAP, adapter);
  }

  public static ByteBuf mapToByteBuf(ByteBufAllocator allocator, Map<String, String> map) {
    if (map == null || map.isEmpty()) {
      return Unpooled.EMPTY_BUFFER;
    }

    ByteBuf byteBuf = allocator.buffer();

    for (Map.Entry<String, String> entry : map.entrySet()) {
      String key = entry.getKey();
      int keyLength = NumberUtils.requireUnsignedShort(ByteBufUtil.utf8Bytes(key));
      byteBuf.writeShort(keyLength);
      ByteBufUtil.reserveAndWriteUtf8(byteBuf, key, keyLength);

      String value = entry.getValue();
      int valueLength = NumberUtils.requireUnsignedShort(ByteBufUtil.utf8Bytes(value));
      byteBuf.writeShort(valueLength);
      ByteBufUtil.reserveAndWriteUtf8(byteBuf, value, keyLength);
    }

    return byteBuf;
  }

  public static Map<String, String> byteBufToMap(ByteBuf byteBuf) {
    Map<String, String> map = new HashMap<>();

    while (byteBuf.readableBytes() > 0) {
      int keyLength = byteBuf.readShort();
      String key = (String) byteBuf.readCharSequence(keyLength, StandardCharsets.UTF_8);

      int valueLength = byteBuf.readShort();
      String value = (String) byteBuf.readCharSequence(valueLength, StandardCharsets.UTF_8);

      map.put(key, value);
    }

    return map;
  }

  public static <T>
      Function<Map<String, String>, Function<? super Publisher<T>, ? extends Publisher<T>>> trace(
          Tracer tracer, String name, Tag... tags) {
    return map ->
        Operators.lift(
            (scannable, subscriber) ->
                new SpanSubscriber<T>(
                    subscriber, subscriber.currentContext(), tracer, map, name, tags));
  }

  public static <T>
      Function<Map<String, String>, Function<? super Publisher<T>, ? extends Publisher<T>>>
          trace() {
    return map -> publisher -> publisher;
  }

  public static <T>
      Function<SpanContext, Function<? super Publisher<T>, ? extends Publisher<T>>> traceAsChild() {
    return (spanContext) -> publisher -> publisher;
  }

  public static <T>
      Function<SpanContext, Function<? super Publisher<T>, ? extends Publisher<T>>> traceAsChild(
          Tracer tracer, String name, Tag... tags) {
    return (spanContext) -> {
      if (spanContext == null) {
        return Operators.lift(
            (scannable, subscriber) ->
                new SpanSubscriber<T>(
                    subscriber, subscriber.currentContext(), tracer, null, name, tags));
      } else {
        return Operators.lift(
            (scannable, subscriber) ->
                new SpanSubscriber<T>(
                    subscriber,
                    subscriber.currentContext(),
                    tracer,
                    null,
                    spanContext,
                    name,
                    tags));
      }
    };
  }
}
