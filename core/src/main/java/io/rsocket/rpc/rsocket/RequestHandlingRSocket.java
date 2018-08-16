package io.rsocket.rpc.rsocket;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.internal.SwitchTransform;
import io.rsocket.rpc.RSocketRpcService;
import io.rsocket.rpc.exception.ServiceNotFound;
import io.rsocket.rpc.frames.Metadata;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RequestHandlingRSocket extends AbstractRSocket {
  private final ConcurrentMap<String, RSocketRpcService> registeredServices =
      new ConcurrentHashMap<>();

  public RequestHandlingRSocket(RSocketRpcService... services) {
    for (RSocketRpcService rsocketService : services) {
      String service = rsocketService.getService();
      registeredServices.put(service, rsocketService);
    }
  }

  public void addService(RSocketRpcService rsocketService) {
    String service = rsocketService.getService();
    registeredServices.put(service, rsocketService);
  }

  @Override
  public Mono<Void> fireAndForget(Payload payload) {
    try {
      ByteBuf metadata = payload.sliceMetadata();
      String service = Metadata.getService(metadata);

      RSocketRpcService rsocketService = registeredServices.get(service);

      if (rsocketService == null) {
        ReferenceCountUtil.safeRelease(payload);
        return Mono.error(new ServiceNotFound(service));
      }

      return rsocketService.fireAndForget(payload);
    } catch (Throwable t) {
      ReferenceCountUtil.safeRelease(payload);
      return Mono.error(t);
    }
  }

  @Override
  public Mono<Payload> requestResponse(Payload payload) {
    try {
      ByteBuf metadata = payload.sliceMetadata();
      String service = Metadata.getService(metadata);

      RSocketRpcService rsocketService = registeredServices.get(service);

      if (rsocketService == null) {
        ReferenceCountUtil.safeRelease(payload);
        return Mono.error(new ServiceNotFound(service));
      }

      return rsocketService.requestResponse(payload);
    } catch (Throwable t) {
      ReferenceCountUtil.safeRelease(payload);
      return Mono.error(t);
    }
  }

  @Override
  public Flux<Payload> requestStream(Payload payload) {
    try {
      ByteBuf metadata = payload.sliceMetadata();
      String service = Metadata.getService(metadata);

      RSocketRpcService rsocketService = registeredServices.get(service);

      if (rsocketService == null) {
        ReferenceCountUtil.safeRelease(payload);
        return Flux.error(new ServiceNotFound(service));
      }

      return rsocketService.requestStream(payload);
    } catch (Throwable t) {
      ReferenceCountUtil.safeRelease(payload);
      return Flux.error(t);
    }
  }

  @Override
  public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
    return new SwitchTransform<>(
        payloads,
        (payload, flux) -> {
          try {
            ByteBuf metadata = payload.sliceMetadata();
            String service = Metadata.getService(metadata);

            RSocketRpcService rsocketService = registeredServices.get(service);

            if (rsocketService == null) {
              ReferenceCountUtil.safeRelease(payload);
              return Flux.error(new ServiceNotFound(service));
            }

            return rsocketService.requestChannel(payload, flux);
          } catch (Throwable t) {
            ReferenceCountUtil.safeRelease(payload);
            return Flux.error(t);
          }
        });
  }
}
