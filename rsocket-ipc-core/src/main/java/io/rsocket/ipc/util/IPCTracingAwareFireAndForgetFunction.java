package io.rsocket.ipc.util;

import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.rsocket.ipc.Functions;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Unmarshaller;
import io.rsocket.rpc.tracing.Tag;
import io.rsocket.rpc.tracing.Tracing;
import reactor.core.publisher.Mono;

public class IPCTracingAwareFireAndForgetFunction implements IPCFunction<Mono<Void>> {

  final String route;
  final Unmarshaller unmarshaller;
  final Marshaller marshaller;
  final Functions.FireAndForget fnf;
  final Tracer tracer;

  public IPCTracingAwareFireAndForgetFunction(
      String route,
      Unmarshaller unmarshaller,
      Marshaller marshaller,
      Functions.FireAndForget fnf,
      Tracer tracer) {
    this.route = route;
    this.unmarshaller = unmarshaller;
    this.marshaller = marshaller;
    this.fnf = fnf;
    this.tracer = tracer;
  }

  @Override
  public Mono<Void> apply(ByteBuf data, ByteBuf metadata, SpanContext context) {
    Object input = unmarshaller.apply(data);
    return fnf.apply(input, metadata)
        .transform(
            Tracing.traceAsChild(
                    tracer,
                    route,
                    Tag.of("rsocket.route", route),
                    Tag.of("rsocket.ipc.role", "server"),
                    Tag.of("rsocket.ipc.version", "ipc"))
                .apply(context));
  }
}
