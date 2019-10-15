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
import io.opentracing.SpanContext;
import io.rsocket.Payload;
import io.rsocket.ResponderRSocket;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.BiFunction;

public interface IPCRSocket extends ResponderRSocket {
    String getService();

    void selfRegister(
            Map<String, BiFunction<Payload, SpanContext, Mono<Void>>> fireAndForgetRegistry,
            Map<String, BiFunction<Payload, SpanContext, Mono<Payload>>> requestResponseRegistry,
            Map<String, BiFunction<Payload, SpanContext, Flux<Payload>>> requestStreamRegistry,
            Map<String, BiFunction<Flux<Payload>, SpanContext, Flux<Payload>>> requestChannelRegistry);

    Flux<Payload> requestChannel(Payload payload, Flux<Payload> publisher);

    default Flux<Payload> requestChannel(Payload payload, Publisher<Payload> payloads) {
        return requestChannel(payload, Flux.from(payloads));
    }
}
