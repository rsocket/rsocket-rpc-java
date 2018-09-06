package io.rsocket.rpc.metrics;

import io.micrometer.core.instrument.*;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Operators;

public class Metrics {
  Metrics() {}

  public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> timed(
      MeterRegistry registry, String name, String... keyValues) {
    return timed(registry, name, Tags.of(keyValues));
  }

  public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> timed(
      MeterRegistry registry, String name, Iterable<Tag> tags) {
    Counter next =
        Counter.builder(name + ".request").tags("status", "next").tags(tags).register(registry);
    Counter complete =
        Counter.builder(name + ".request").tags("status", "complete").tags(tags).register(registry);
    Counter error =
        Counter.builder(name + ".request").tags("status", "error").tags(tags).register(registry);
    Counter cancelled =
        Counter.builder(name + ".request")
            .tags("status", "cancelled")
            .tags(tags)
            .register(registry);
    Timer timer =
        Timer.builder(name + ".latency")
            .publishPercentiles(0.5, 0.9, 0.95, 0.99)
            .tags(tags)
            .register(registry);
    return Operators.lift(
        (scannable, subscriber) ->
            new MetricsSubscriber<>(subscriber, next, complete, error, cancelled, timer));
  }
}
