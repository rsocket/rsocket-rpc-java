package io.rsocket.rpc.quickstart.service;

import io.netty.buffer.ByteBuf;
import io.rsocket.rpc.quickstart.service.protobuf.HelloRequest;
import io.rsocket.rpc.quickstart.service.protobuf.HelloResponse;
import io.rsocket.rpc.quickstart.service.protobuf.HelloService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class DefaultHelloService implements HelloService {
  static final Logger LOG = LoggerFactory.getLogger(DefaultHelloService.class);

  @Override
  public Mono<HelloResponse> sayHello(HelloRequest message, ByteBuf metadata) {
    LOG.info("Got message {}", message.getName());

    return message.getName().contains("Ping")
        ? Mono.just(HelloResponse.newBuilder().setMessage("Pong").buildPartial())
        : Mono.just(
            HelloResponse.newBuilder().setMessage("Hello " + message.getName()).buildPartial());
  }
}
