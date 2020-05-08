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
package io.rsocket.ipc.util;

import io.netty.buffer.ByteBuf;
import io.opentracing.Tracer;
import io.rsocket.Payload;
import io.rsocket.ipc.Functions;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.MetadataDecoder;
import io.rsocket.ipc.Unmarshaller;
import io.rsocket.ipc.tracing.Tag;
import io.rsocket.ipc.tracing.Tracing;
import io.rsocket.util.ByteBufPayload;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

@SuppressWarnings("rawtypes")
public class IPCTracingAwareRequestChannelFunction implements IPCChannelFunction {

  final String route;
  final Unmarshaller unmarshaller;
  final Marshaller marshaller;
  final Functions.HandleRequestHandle rs;
  final Tracer tracer;

  public IPCTracingAwareRequestChannelFunction(
      String route,
      Unmarshaller unmarshaller,
      Marshaller marshaller,
      Functions.HandleRequestHandle rs,
      Tracer tracer) {
    this.route = route;
    this.unmarshaller = unmarshaller;
    this.marshaller = marshaller;
    this.rs = rs;
    this.tracer = tracer;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Flux<Payload> apply(
      Flux<Payload> source, Payload payload, MetadataDecoder.Metadata metadata) {
    return rs.apply(
            unmarshaller.apply(payload.sliceData()),
            source.map(
                p -> {
                  try {
                    ByteBuf dd = p.sliceData();
                    Object result = unmarshaller.apply(dd);
                    p.release();
                    return result;
                  } catch (Throwable t) {
                    p.release();
                    throw Exceptions.propagate(t);
                  }
                }),
            metadata.metadata())
        .map(o -> ByteBufPayload.create(marshaller.apply(o)))
        .transform(
            Tracing.traceAsChild(
                    tracer,
                    route,
                    Tag.of("rsocket.route", route),
                    Tag.of("rsocket.ipc.role", "server"),
                    Tag.of("rsocket.ipc.version", "ipc"))
                .apply(metadata.spanContext()));
  }
}
