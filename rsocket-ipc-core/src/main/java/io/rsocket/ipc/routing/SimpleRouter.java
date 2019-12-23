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
package io.rsocket.ipc.routing;

import io.rsocket.Payload;
import io.rsocket.ipc.MutableRouter;
import io.rsocket.ipc.util.IPCChannelFunction;
import io.rsocket.ipc.util.IPCFunction;
import java.util.HashMap;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SimpleRouter implements MutableRouter<SimpleRouter> {
  final Map<String, IPCFunction<Mono<Void>>> metadataPushRegistry;
  final Map<String, IPCFunction<Mono<Void>>> fireAndForgetRegistry;
  final Map<String, IPCFunction<Mono<Payload>>> requestResponseRegistry;
  final Map<String, IPCFunction<Flux<Payload>>> requestStreamRegistry;
  final Map<String, IPCChannelFunction> requestChannelRegistry;

  public SimpleRouter() {
    this.metadataPushRegistry = new HashMap<>();
    this.fireAndForgetRegistry = new HashMap<>();
    this.requestResponseRegistry = new HashMap<>();
    this.requestStreamRegistry = new HashMap<>();
    this.requestChannelRegistry = new HashMap<>();
  }

  @Override
  public IPCFunction<Mono<Void>> routeFireAndForget(String route) {
    return fireAndForgetRegistry.get(route);
  }

  @Override
  public IPCFunction<Mono<Payload>> routeRequestResponse(String route) {
    return requestResponseRegistry.get(route);
  }

  @Override
  public IPCFunction<Flux<Payload>> routeRequestStream(String route) {
    return requestStreamRegistry.get(route);
  }

  @Override
  public IPCChannelFunction routeRequestChannel(String route) {
    return requestChannelRegistry.get(route);
  }

  @Override
  public IPCFunction<Mono<Void>> routeMetadataPush(String route) {
    return metadataPushRegistry.get(route);
  }

  @Override
  public SimpleRouter withFireAndForgetRoute(String route, IPCFunction<Mono<Void>> function) {
    fireAndForgetRegistry.put(route, function);
    return this;
  }

  @Override
  public SimpleRouter withRequestResponseRoute(String route, IPCFunction<Mono<Payload>> function) {
    requestResponseRegistry.put(route, function);
    return this;
  }

  @Override
  public SimpleRouter withRequestStreamRoute(String route, IPCFunction<Flux<Payload>> function) {
    requestStreamRegistry.put(route, function);
    return this;
  }

  @Override
  public SimpleRouter withRequestChannelRoute(String route, IPCChannelFunction function) {
    requestChannelRegistry.put(route, function);
    return this;
  }

  @Override
  public SimpleRouter withMetadataPushRoute(String route, IPCFunction<Mono<Void>> function) {
    metadataPushRegistry.put(route, function);
    return this;
  }

  public Map<String, IPCFunction<Mono<Void>>> getMetadataPushRegistry() {
    return metadataPushRegistry;
  }

  public Map<String, IPCFunction<Mono<Void>>> getFireAndForgetRegistry() {
    return fireAndForgetRegistry;
  }

  public Map<String, IPCFunction<Mono<Payload>>> getRequestResponseRegistry() {
    return requestResponseRegistry;
  }

  public Map<String, IPCFunction<Flux<Payload>>> getRequestStreamRegistry() {
    return requestStreamRegistry;
  }

  public Map<String, IPCChannelFunction> getRequestChannelRegistry() {
    return requestChannelRegistry;
  }
}
