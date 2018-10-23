package io.rsocket.rpc.quickstart.service;

import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.rpc.quickstart.service.protobuf.HelloRequest;
import io.rsocket.rpc.quickstart.service.protobuf.HelloResponse;
import io.rsocket.rpc.quickstart.service.protobuf.HelloServiceClient;
import io.rsocket.rpc.quickstart.service.protobuf.HelloServiceServer;
import io.rsocket.rpc.rsocket.RequestHandlingRSocket;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import java.time.Duration;
import java.util.Optional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class Main {

  public static void main(String[] args) {
    HelloServiceServer helloServiceServer =
        new HelloServiceServer(new DefaultHelloService(), Optional.empty(), Optional.empty());
    CloseableChannel closeableChannel =
        RSocketFactory.receive()
            .acceptor(
                (setup, sendingSocket) -> Mono.just(new RequestHandlingRSocket(helloServiceServer)))
            .transport(TcpServerTransport.create(8081))
            .start()
            .block();

    RSocket rSocket =
        RSocketFactory.connect().transport(TcpClientTransport.create(8081)).start().block();
    HelloServiceClient helloServiceClient = new HelloServiceClient(rSocket);

    Flux.interval(Duration.ofSeconds(1))
        .flatMap(
            i ->
                i % 5 == 0
                    ? helloServiceClient.sayHello(HelloRequest.newBuilder().setName("Ping").build())
                    : helloServiceClient.sayHello(
                        HelloRequest.newBuilder().setName(String.valueOf(i)).build()))
        .map(HelloResponse::getMessage)
        .log()
        .subscribe();

    closeableChannel.onClose().block();
  }
}
