/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.rsocket.ipc.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import reactor.core.Fuseable;
import reactor.core.publisher.Operators;

public class Metrics {
  Metrics() {}

  public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> timed(
      MeterRegistry registry, String name, String... keyValues) {
    return timed(registry, name, Tags.of(keyValues));
  }

  @SuppressWarnings("unchecked")
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
        (scannable, subscriber) -> {
          if (scannable instanceof Fuseable) {
            if (subscriber instanceof Fuseable.ConditionalSubscriber) {
              return new MetricsFuseableConditionalSubscriber<>(
                  (Fuseable.ConditionalSubscriber<? super T>) subscriber,
                  next,
                  complete,
                  error,
                  cancelled,
                  timer);
            } else {
              return new MetricsFuseableSubscriber<>(
                  subscriber, next, complete, error, cancelled, timer);
            }
          } else {
            if (subscriber instanceof Fuseable.ConditionalSubscriber) {
              return new MetricsConditionalSubscriber<>(
                  (Fuseable.ConditionalSubscriber<? super T>) subscriber,
                  next,
                  complete,
                  error,
                  cancelled,
                  timer);
            } else {
              return new MetricsSubscriber<>(subscriber, next, complete, error, cancelled, timer);
            }
          }
        });
  }
}
