package io.rsocket.rpc.showcase.service;

import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.rpc.rsocket.RequestHandlingRSocket;
import io.rsocket.rpc.showcase.service.protobuf.SimpleRequest;
import io.rsocket.rpc.showcase.service.protobuf.SimpleResponse;
import io.rsocket.rpc.showcase.service.protobuf.SimpleServiceClient;
import io.rsocket.rpc.showcase.service.protobuf.SimpleServiceServer;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import java.util.Optional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class Main {

  public static void main(String[] args) {
    SimpleServiceServer serviceServer =
        new SimpleServiceServer(new DefaultSimpleService(), Optional.empty(), Optional.empty());
    CloseableChannel closeableChannel =
        RSocketFactory.receive()
            .acceptor(
                (setup, sendingSocket) -> Mono.just(new RequestHandlingRSocket(serviceServer)))
            .transport(TcpServerTransport.create(8081))
            .start()
            .block();

    RSocket rSocket =
        RSocketFactory.connect().transport(TcpClientTransport.create(8081)).start().block();

    SimpleServiceClient client = new SimpleServiceClient(rSocket);

    Flux<SimpleRequest> requests =
        Flux.range(1, 11)
            .map(i -> "sending -> " + i)
            .map(s -> SimpleRequest.newBuilder().setRequestMessage(s).build());

    SimpleResponse response = client.streamingRequestSingleResponse(requests).log().block();

    System.out.println(response.getResponseMessage());
  }
}
