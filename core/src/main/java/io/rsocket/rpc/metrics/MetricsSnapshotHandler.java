package io.rsocket.rpc.metrics;

import io.rsocket.rpc.metrics.om.MetricsSnapshot;
import io.rsocket.rpc.metrics.om.Skew;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

public interface MetricsSnapshotHandler {
  Flux<Skew> streamMetrics(Publisher<MetricsSnapshot> messages, io.netty.buffer.ByteBuf metadata);
}
