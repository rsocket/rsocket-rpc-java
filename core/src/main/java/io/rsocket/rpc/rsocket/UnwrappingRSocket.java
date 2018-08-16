package io.rsocket.rpc.rsocket;

import io.netty.buffer.ByteBuf;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.rpc.frames.*;
import io.rsocket.util.ByteBufPayload;
import io.rsocket.util.RSocketProxy;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// Need to unwrap RSocketRpc Messages
public class UnwrappingRSocket extends RSocketProxy {

  public UnwrappingRSocket(RSocket source) {
    super(source);
  }

  private Payload unwrap(Payload payload) {
    try {
      ByteBuf data = payload.sliceData();
      ByteBuf metadata = payload.sliceMetadata();
      ByteBuf unwrappedMetadata;
      FrameType frameType = FrameHeaderFlyweight.frameType(metadata);
      switch (frameType) {
        case DESTINATION:
          unwrappedMetadata = DestinationFlyweight.metadata(metadata);
          break;
        case GROUP:
          unwrappedMetadata = GroupFlyweight.metadata(metadata);
          break;
        case BROADCAST:
          unwrappedMetadata = BroadcastFlyweight.metadata(metadata);
          break;
        case SHARD:
          unwrappedMetadata = ShardFlyweight.metadata(metadata);
          break;
        default:
          throw new IllegalStateException("unknown frame type " + frameType);
      }

      return ByteBufPayload.create(data.retain(), unwrappedMetadata.retain());
    } finally {
      payload.release();
    }
  }

  @Override
  public Mono<Void> fireAndForget(Payload payload) {
    try {
      return super.fireAndForget(unwrap(payload));
    } catch (Throwable t) {
      return Mono.error(t);
    }
  }

  @Override
  public Mono<Payload> requestResponse(Payload payload) {
    try {
      return super.requestResponse(unwrap(payload));
    } catch (Throwable t) {
      return Mono.error(t);
    }
  }

  @Override
  public Flux<Payload> requestStream(Payload payload) {
    try {
      return super.requestStream(unwrap(payload));
    } catch (Throwable t) {
      return Flux.error(t);
    }
  }

  @Override
  public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
    return super.requestChannel(Flux.from(payloads).map(this::unwrap));
  }

  @Override
  public Mono<Void> metadataPush(Payload payload) {
    try {
      return super.metadataPush(unwrap(payload));
    } catch (Throwable t) {
      return Mono.error(t);
    }
  }
}
