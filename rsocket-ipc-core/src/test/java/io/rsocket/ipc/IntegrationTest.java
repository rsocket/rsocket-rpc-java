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

import io.netty.buffer.ByteBufAllocator;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.ipc.decoders.CompositeMetadataDecoder;
import io.rsocket.ipc.encoders.DefaultMetadataEncoder;
import io.rsocket.ipc.marshallers.Primitives;
import io.rsocket.ipc.marshallers.Strings;
import io.rsocket.transport.local.LocalClientTransport;
import io.rsocket.transport.local.LocalServerTransport;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class IntegrationTest {
  @Test
  public void test() {
    RequestHandlingRSocket requestHandler =
        new RequestHandlingRSocket(new CompositeMetadataDecoder());

    RSocketFactory.receive()
        .acceptor((setup, sendingSocket) -> Mono.just(requestHandler))
        .transport(LocalServerTransport.create("test-local-server"))
        .start()
        .block();

    RSocket rsocket =
        RSocketFactory.connect()
            .transport(LocalClientTransport.create("test-local-server"))
            .start()
            .block();

    AtomicBoolean ff = new AtomicBoolean();

    IPCRSocket service =
        Server.service("HelloService")
            .noMeterRegistry()
            .noTracer()
            .marshall(Strings.marshaller())
            .unmarshall(Strings.unmarshaller())
            .requestResponse("hello", (s, byteBuf) -> Mono.just("Hello -> " + s))
            .requestResponse("goodbye", (s, byteBuf) -> Mono.just("Goodbye -> " + s))
            .requestResponse(
                "count",
                Primitives.intMarshaller(),
                (charSequence, byteBuf) -> Mono.just(charSequence.length()))
            .requestResponse(
                "increment",
                Primitives.intUnmarshaller(),
                Primitives.intMarshaller(),
                (integer, byteBuf) -> Mono.just(integer + 1))
            .requestStream(
                "helloStream", (s, byteBuf) -> Flux.range(1, 10).map(i -> i + " - Hello -> " + s))
            .requestStream(
                "toString",
                Primitives.longUnmarshaller(),
                (aLong, byteBuf) -> Flux.just(String.valueOf(aLong)))
            .fireAndForget(
                "ff",
                (s, byteBuf) -> {
                  ff.set(true);
                  return Mono.empty();
                })
            .requestChannel("helloChannel", (s, publisher, byteBuf) -> Flux.just("Hello -> " + s))
            .toIPCRSocket();

    requestHandler.withEndpoint(service);

    Client<CharSequence, String> helloService =
        Client.service("HelloService")
            .rsocket(rsocket)
            .customMetadataEncoder(new DefaultMetadataEncoder(ByteBufAllocator.DEFAULT))
            .noMeterRegistry()
            .noTracer()
            .marshall(Strings.marshaller())
            .unmarshall(Strings.unmarshaller());

    String r1 = helloService.requestResponse("hello").apply("Alice").block();
    Assert.assertEquals("Hello -> Alice", r1);

    String r2 = helloService.requestResponse("goodbye").apply("Bob").block();
    Assert.assertEquals("Goodbye -> Bob", r2);

    StepVerifier.create(helloService.requestStream("helloStream").apply("Carol"))
        .expectNextCount(10)
        .expectComplete()
        .verify();

    helloService.fireAndForget("ff").apply("boom").block();
    Assert.assertTrue(ff.get());

    String r3 = helloService.requestChannel("helloChannel").apply(Mono.just("Eve")).blockLast();
    Assert.assertEquals("Hello -> Eve", r3);

    int count =
        helloService.requestResponse("count", Primitives.intUnmarshaller()).apply("hello").block();
    Assert.assertEquals(5, count);

    long l = System.currentTimeMillis();
    String toString =
        helloService.requestStream("toString", Primitives.longMarshaller()).apply(l).blockLast();
    Assert.assertEquals(String.valueOf(l), toString);

    Integer increment =
        helloService
            .requestResponse("increment", Primitives.intMarshaller(), Primitives.intUnmarshaller())
            .apply(1)
            .block();
    Assert.assertEquals(2, increment.intValue());
  }
}
