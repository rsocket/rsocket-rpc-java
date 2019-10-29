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

import io.opentracing.Tracer;
import io.rsocket.Payload;
import io.rsocket.ipc.util.IPCChannelFunction;
import io.rsocket.ipc.util.IPCFunction;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SuppressWarnings("unchecked")
class IPCServerRSocket extends RoutingServerRSocket implements IPCRSocket, SelfRegistrable {

  private final String service;

  public IPCServerRSocket(
      String service,
      Map<String, IPCFunction<Mono<Void>>> fireAndForgetRegistry,
      Map<String, IPCFunction<Mono<Payload>>> requestResponseRegistry,
      Map<String, IPCFunction<Flux<Payload>>> requestStreamRegistry,
      Map<String, IPCChannelFunction> requestChannelRegistry) {
    super(
        fireAndForgetRegistry,
        requestResponseRegistry,
        requestStreamRegistry,
        requestChannelRegistry);
    this.service = service;
  }

  public IPCServerRSocket(
      String service,
      Tracer tracer,
      Map<String, IPCFunction<Mono<Void>>> fireAndForgetRegistry,
      Map<String, IPCFunction<Mono<Payload>>> requestResponseRegistry,
      Map<String, IPCFunction<Flux<Payload>>> requestStreamRegistry,
      Map<String, IPCChannelFunction> requestChannelRegistry) {
    super(
        tracer,
        fireAndForgetRegistry,
        requestResponseRegistry,
        requestStreamRegistry,
        requestChannelRegistry);
    this.service = service;
  }

  public IPCServerRSocket(
      String service,
      MetadataDecoder decoder,
      Map<String, IPCFunction<Mono<Void>>> fireAndForgetRegistry,
      Map<String, IPCFunction<Mono<Payload>>> requestResponseRegistry,
      Map<String, IPCFunction<Flux<Payload>>> requestStreamRegistry,
      Map<String, IPCChannelFunction> requestChannelRegistry) {
    super(
        decoder,
        fireAndForgetRegistry,
        requestResponseRegistry,
        requestStreamRegistry,
        requestChannelRegistry);
    this.service = service;
  }

  @Override
  public String getService() {
    return service;
  }

  @Override
  public void selfRegister(
      Map<String, IPCFunction<Mono<Void>>> fireAndForgetRegistry,
      Map<String, IPCFunction<Mono<Payload>>> requestResponseRegistry,
      Map<String, IPCFunction<Flux<Payload>>> requestStreamRegistry,
      Map<String, IPCChannelFunction> requestChannelRegistry) {
    fireAndForgetRegistry.putAll(this.fireAndForgetRegistry);
    requestResponseRegistry.putAll(this.requestResponseRegistry);
    requestStreamRegistry.putAll(this.requestStreamRegistry);
    requestChannelRegistry.putAll(this.requestChannelRegistry);
  }

  @Override
  public Flux<Payload> requestChannel(Payload payload, Flux<Payload> payloads) {
    return super.requestChannel(payloads);
  }
}
