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
import io.rsocket.ipc.routing.SimpleRouter;
import reactor.core.publisher.Flux;

@SuppressWarnings("unchecked")
class IPCServerRSocket extends RoutingServerRSocket implements IPCRSocket, SelfRegistrable {

  private final String service;
  private final SimpleRouter simpleRouter;

  public IPCServerRSocket(String service, SimpleRouter simpleRouter) {
    super(simpleRouter);
    this.service = service;
    this.simpleRouter = simpleRouter;
  }

  public IPCServerRSocket(String service, Tracer tracer, SimpleRouter simpleRouter) {
    super(tracer, simpleRouter);
    this.service = service;
    this.simpleRouter = simpleRouter;
  }

  public IPCServerRSocket(String service, MetadataDecoder decoder, SimpleRouter simpleRouter) {
    super(decoder, simpleRouter);
    this.service = service;
    this.simpleRouter = simpleRouter;
  }

  @Override
  public String getService() {
    return service;
  }

  @Override
  public void selfRegister(MutableRouter mutableRouter) {
    simpleRouter.getFireAndForgetRegistry().forEach(mutableRouter::withFireAndForgetRoute);
    simpleRouter.getRequestResponseRegistry().forEach(mutableRouter::withRequestResponseRoute);
    simpleRouter.getRequestStreamRegistry().forEach(mutableRouter::withRequestStreamRoute);
    simpleRouter.getRequestChannelRegistry().forEach(mutableRouter::withRequestChannelRoute);
  }

  @Override
  public Flux<Payload> requestChannel(Payload payload, Flux<Payload> payloads) {
    return super.requestChannel(payloads);
  }
}
