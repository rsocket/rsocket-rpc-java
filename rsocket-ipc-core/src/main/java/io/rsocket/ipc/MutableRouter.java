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

import io.rsocket.Payload;
import io.rsocket.ipc.util.IPCChannelFunction;
import io.rsocket.ipc.util.IPCFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MutableRouter<SELF extends MutableRouter<SELF>> extends Router {
  SELF withFireAndForgetRoute(String route, IPCFunction<Mono<Void>> function);

  SELF withRequestResponseRoute(String route, IPCFunction<Mono<Payload>> function);

  SELF withRequestStreamRoute(String route, IPCFunction<Flux<Payload>> function);

  SELF withRequestChannelRoute(String route, IPCChannelFunction function);
}
