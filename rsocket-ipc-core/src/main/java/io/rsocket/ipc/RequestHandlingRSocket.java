package io.rsocket.ipc;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import io.opentracing.SpanContext;
import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.ResponderRSocket;
import io.rsocket.ipc.util.TriFunction;
import io.rsocket.rpc.RSocketRpcService;
import io.rsocket.rpc.exception.ServiceNotFound;
import io.rsocket.rpc.frames.Metadata;
import io.rsocket.rpc.tracing.Tracing;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class RequestHandlingRSocket<T> extends AbstractRSocket implements ResponderRSocket {

  private final Map<String, Handler<Payload, Mono<Void>>> fireAndForgetRegistry = new ConcurrentHashMap<>();
  private final Map<String, Handler<Payload, Mono<Payload>>> requestResponseRegistry = new ConcurrentHashMap<>();
  private final Map<String, Handler<Payload, Flux<Payload>>> requestStreamRegistry = new ConcurrentHashMap<>();
  private final Map<String, Handler<Flux<Payload>, Flux<Payload>>> requestChannelRegistry = new ConcurrentHashMap<>();

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
      Mono<Void> response = decoder.decode(payload, (route, spanContext) -> {
        BiFunction<Payload, SpanContext, Mono<Void>> ffContext = this.fireAndForgetRegistry.get(route);

        if (ffContext == null) {
          return Mono.error(
                  new NullPointerException("nothing found for route " + route));
        }

        return ffContext.apply(payload, spanContext);
      });

      payload.release();

      return response;
    } catch (Throwable t) {
      payload.release();
      return Mono.error(t);
    }
  }

  @Override
  public Mono<Payload> requestResponse(Payload payload) {
    try {
      Mono<Payload> response = decoder.decode(payload, (route, spanContext) -> {
        BiFunction<Payload, SpanContext, Mono<Payload>> ffContext = this.requestResponseRegistry.get(route);

        if (ffContext == null) {
          return Mono.error(
                  new NullPointerException("nothing found for route " + route));
        }

        return ffContext.apply(payload, spanContext);
      });

      payload.release();

      return response;
    } catch (Throwable t) {
      payload.release();
      return Mono.error(t);
    }
  }

  @Override
  public Flux<Payload> requestStream(Payload payload) {
    try {
      Flux<Payload> response = decoder.decode(payload, (route, spanContext) -> {
        BiFunction<Payload, SpanContext, Flux<Payload>> ffContext = this.requestStreamRegistry.get(route);

        if (ffContext == null) {
          return Flux.error(
                  new NullPointerException("nothing found for route " + route));
        }

        return ffContext.apply(payload, spanContext);
      });

      payload.release();

      return response;
    } catch (Throwable t) {
      payload.release();
      return Flux.error(t);
    }
  }

  @Override
  public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
    return Flux.from(payloads)
        .switchOnFirst(
            (firstSignal, flux) -> {
              if (firstSignal.hasValue()) {
                Payload payload = firstSignal.get();
                try {
                  return decoder.decode(payload, (route, spanContext) -> {
                    BiFunction<Flux<Payload>, SpanContext, Flux<Payload>> ffContext = this.requestChannelRegistry.get(route);

                    if (ffContext == null) {
                      payload.release();
                      return Flux.error(
                              new NullPointerException("nothing found for route " + route));
                    }

                    return ffContext.apply(flux, spanContext);
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
        BiFunction<Flux<Payload>, SpanContext, Flux<Payload>> ffContext = this.requestChannelRegistry.get(route);

        if (ffContext == null) {
          payload.release();
          return Flux.error(
                  new NullPointerException("nothing found for route " + route));
        }

        return ffContext.apply(Flux.from(payloads), spanContext);
      });
    } catch (Throwable t) {
      payload.release();
      return Flux.error(t);
    }
  }
}
