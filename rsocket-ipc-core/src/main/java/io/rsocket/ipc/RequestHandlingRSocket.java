package io.rsocket.ipc;

import io.netty.buffer.ByteBuf;
import io.opentracing.SpanContext;
import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.ResponderRSocket;
import io.rsocket.ipc.util.IPCChannelFunction;
import io.rsocket.ipc.util.IPCFunction;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RequestHandlingRSocket<T> extends AbstractRSocket implements ResponderRSocket {

  private final Map<String, IPCFunction<Mono<Void>>> fireAndForgetRegistry = new ConcurrentHashMap<>();
  private final Map<String, IPCFunction<Mono<Payload>>> requestResponseRegistry = new ConcurrentHashMap<>();
  private final Map<String, IPCFunction<Flux<Payload>>> requestStreamRegistry = new ConcurrentHashMap<>();
  private final Map<String, IPCChannelFunction> requestChannelRegistry = new ConcurrentHashMap<>();


  private final MetadataDecoder decoder;

  public RequestHandlingRSocket(MetadataDecoder decoder) {
    this.decoder = decoder;
  }

  public RequestHandlingRSocket withService(IPCRSocket rSocketRpcService) {
    rSocketRpcService
      .selfRegister(fireAndForgetRegistry, requestResponseRegistry, requestStreamRegistry, requestChannelRegistry);
    return this;
  }

  @Override
  public Mono<Void> fireAndForget(Payload payload) {
    try {
      Mono<Void> response = decoder.decode(payload, this::doDecodeAndHandleFireAndForget);

      payload.release();

      return response;
    } catch (Throwable t) {
      payload.release();
      return Mono.error(t);
    }
  }

  Mono<Void> doDecodeAndHandleFireAndForget(ByteBuf data, ByteBuf metadata, String route, SpanContext spanContext) {
    IPCFunction<Mono<Void>> monoIPCFunction = this.fireAndForgetRegistry.get(route);

    if (monoIPCFunction == null) {
      return Mono.error(
              new NullPointerException("nothing found for route " + route));
    }

    return monoIPCFunction.apply(data, metadata, spanContext);
  }

  @Override
  public Mono<Payload> requestResponse(Payload payload) {
    try {
      Mono<Payload> response = decoder.decode(payload, this::doDecodeAndHandleRequestResponse);

      payload.release();

      return response;
    } catch (Throwable t) {
      payload.release();
      return Mono.error(t);
    }
  }

  Mono<Payload> doDecodeAndHandleRequestResponse(ByteBuf data, ByteBuf metadata, String route, SpanContext spanContext) {
    IPCFunction<Mono<Payload>> monoIPCFunction = this.requestResponseRegistry.get(route);

    if (monoIPCFunction == null) {
      return Mono.error(
              new NullPointerException("nothing found for route " + route));
    }

    return monoIPCFunction.apply(data, metadata, spanContext);
  }

  @Override
  public Flux<Payload> requestStream(Payload payload) {
    try {
      Flux<Payload> response = decoder.decode(payload, this::doDecodeAndHandleRequestStream);

      payload.release();

      return response;
    } catch (Throwable t) {
      payload.release();
      return Flux.error(t);
    }
  }

  public Flux<Payload> doDecodeAndHandleRequestStream(ByteBuf data, ByteBuf metadata, String route, SpanContext spanContext) {
    IPCFunction<Flux<Payload>> ffContext = this.requestStreamRegistry.get(route);

    if (ffContext == null) {
      return Flux.error(
              new NullPointerException("nothing found for route " + route));
    }

    return ffContext.apply(data, metadata, spanContext);
  }

  @Override
  public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
    return Flux.from(payloads)
        .switchOnFirst(
            (firstSignal, flux) -> {
              if (firstSignal.hasValue()) {
                Payload payload = firstSignal.get();
                try {
                  return decoder.decode(payload, (ByteBuf data, ByteBuf metadata, String route, SpanContext spanContext) -> {
                    IPCChannelFunction ffContext = this.requestChannelRegistry.get(route);

                    if (ffContext == null) {
                      payload.release();
                      return Flux.error(
                              new NullPointerException("nothing found for route " + route));
                    }

                    return ffContext.apply(Flux.from(payloads), data, metadata, spanContext);
                  });
                } catch (Throwable t) {
                  payload.release();
                  return Flux.error(t);
                }
              }

              return flux;
            });
  }

  @Override
  public Flux<Payload> requestChannel(Payload payload, Publisher<Payload> payloads) {
    try {
      return decoder.decode(payload, (data, metadata, route, spanContext) -> {
        IPCChannelFunction ffContext = this.requestChannelRegistry.get(route);

        if (ffContext == null) {
          payload.release();
          return Flux.error(
                  new NullPointerException("nothing found for route " + route));
        }

        return ffContext.apply(Flux.from(payloads), data, metadata, spanContext);
      });
    } catch (Throwable t) {
      payload.release();
      return Flux.error(t);
    }
  }
}
