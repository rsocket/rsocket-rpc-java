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
package io.rsocket.ipc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.rsocket.ipc.util.TriFunction;
import java.util.function.BiFunction;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class Functions {
  private Functions() {}

  @FunctionalInterface
  public interface RequestResponse<I, O> extends BiFunction<I, ByteBuf, Mono<O>> {
    @Override
    Mono<O> apply(I i, ByteBuf byteBuf);

    default Mono<O> apply(I i) {
      return apply(i, Unpooled.EMPTY_BUFFER);
    }
  }

  @FunctionalInterface
  public interface RequestStream<I, O> extends BiFunction<I, ByteBuf, Flux<O>> {
    @Override
    Flux<O> apply(I i, ByteBuf byteBuf);

    default Flux<O> apply(I i) {
      return apply(i, Unpooled.EMPTY_BUFFER);
    }
  }

  @FunctionalInterface
  public interface HandleRequestHandle<I, O>
      extends TriFunction<I, Publisher<I>, ByteBuf, Flux<O>> {
    @Override
    Flux<O> apply(I i, Publisher<I> publisher, ByteBuf byteBuf);

    default Flux<O> apply(I i, Publisher<I> publisher) {
      return apply(i, publisher, Unpooled.EMPTY_BUFFER);
    }
  }

  @FunctionalInterface
  public interface RequestChannel<I, O> extends BiFunction<Publisher<I>, ByteBuf, Flux<O>> {
    @Override
    Flux<O> apply(Publisher<I> publisher, ByteBuf byteBuf);

    default Flux<O> apply(Publisher<I> publisher) {
      return apply(publisher, Unpooled.EMPTY_BUFFER);
    }
  }

  @FunctionalInterface
  public interface FireAndForget<I> extends BiFunction<I, ByteBuf, Mono<Void>> {
    @Override
    Mono<Void> apply(I i, ByteBuf byteBuf);

    default Mono<Void> apply(I i) {
      return apply(i, Unpooled.EMPTY_BUFFER);
    }
  }
}
