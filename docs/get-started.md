# Get Started with RSocket RPC Java

In this section we are going to learn the essential of RSocket-RPC and its implementation in Java

## Introduction
RSocket RPC is an easy to use RPC layer that sits on top of [RSocket](http://rsocket.io). RSocket is a binary protocol for use on byte stream transports such as TCP, WebSockets, and [Aeron](https://github.com/real-logic/aeron). RSocket RPC is able to leverage RSockets unique features allowing you develop complex realtime, streaming applications without needing Kafka, Spark, etc. It is completely non-blocking and reactive making it responsive and high performance.

### Protobuf
RSocket RPC use [Protobuf 3](https://developers.google.com/protocol-buffers/docs/proto3) as its IDL. Protobuf works well with RSocket because they are both binary. Using Protobuf makes it easy to switch from gRPC. You only need to replace the gRPC compiler with the RSocket RPC compiler to start using RSocket RPC.

### Full Duplex
RSocket RPC is built with a full duplex protocol- RSocket. An application that initiates a connection (traditionally a client) can actually serve requests from the application it is connecting with (traditionally the server).  In other words a client can connect to a server, and the server can call a RSocket RPC service located on the client. The server does not have to wait for the client to initiate a call, only the connection is necessary.

### Reactive Streams Compatible
RSocket RPC's Java implementation is built using a Reactive Streams compliant library [Reactor-core](https://github.com/reactor/reactor-core). RSocket RPC generated code returns either a Flux which is a stream of many, or a Mono which is a stream of one. RSocket RPC is compatible with any [Reactive Streams](http://www.reactive-streams.org/) implementation including RXJava 2, and Akka Streams. 

### Back-pressure
RSocket RPC respects both RSocket and Reactive Stream back-pressure. Back-pressure is where the consumer of a stream sends stream's producer a message indicating how many messages it can handle. This makes RSocket RPC services very reliable, and  not be overwhelmed with demand.

## Interaction Models
RSocket RPC has five interaction models. They are modeled using a Protobuf 3 IDL. This section details the interaction models, and gives an example Protobuf IDL definition. The interaction models are request/response, request/stream, fire-and-forget, streaming request/one response, and streaming request/streaming response.

### Request / Response
Request response is analogous to a HTTP Rest call. A major difference is because this is non-blocking the caller can wait for the response for a long time without blocking other requests on the same connection.

#### Protobuf
```
rpc RequestReply (SimpleRequest) returns (SimpleResponse) {}
```

### Fire-and-Forget
Fire and forget sends a request without a response. This is not just ignoring the response; the underlying protocol does not send anything back to the caller. To make a request fire-and-forget with RSocket RPC you need to return google.protobuf.Empty in your IDL. This will generate fire and forget code.

#### Protobuf
```
import "google/protobuf/empty.proto";
...
rpc FireAndForget (SimpleRequest) returns (google.protobuf.Empty) {}
```

### Single Request / Stream Response
Sends a single request and then receives multiple responses. This can be used to model subscriptions to topics and queues. It can be also used to do things like page from a database. To generate this mark the response with the stream keyword.

#### Protobuf
```
rpc RequestStream (SimpleRequest) returns (stream SimpleResponse) {}
```

### Streaming Request / Single Response
Sends a stream of requests, and receives a single response.  This can be used to model transactions. You can send multiple requests over a logic channel, and then receive a single response if they were processed correctly or not. Mark the request with the stream keyword to create this interaction model.


#### Protobuf
```
rpc StreamingRequestSingleResponse (stream SimpleRequest) returns (SimpleResponse) {}
```

### Streaming Request / Streaming Response
Sends request stream and returns response stream. This models a fully duplex interaction. Mark the request and response with the stream keyword to create this interaction model.

#### Protobuf
```
rpc StreamingRequestAndResponse (stream SimpleRequest) returns (stream SimpleResponse) {}
```

## Getting Started
### Prerequisites
* JDK 1.8
* Gradle 2.x
* Protobuf Compiler 3.x

#### Installing Protobuf
##### Mac OS
For Mac users you can easily install the Protobuf compiler using Homebrew:
```
$ brew install Protobuf
```
##### Ubuntu
Ubuntu users can install Protobuf using apt-get:
```
$ sudo apt-get install libProtobuf-java Protobuf-compiler
```

### Configuring Gradle
RSocket RPC Java uses a Protobuf plugin to generate application code. Add the following code to your project's Gradle file so that it will generate code from your Protobuf IDL when your application is compiled.

```
protobuf {
    protoc {
        artifact = 'com.google.protobuf:protoc:3.6.1'
    }
    plugins {
        rsocketRpc {
            artifact = 'io.rsocket.rpc:rsocket-rpc-protobuf:0.2.4'
        }
    }
    generateProtoTasks {
        all()*.plugins {
            rsocketRpc {}
        }
    }
}

// If you use Intellij add this so it can find the generated classes
idea {
  module {
	sourceDirs += file("src/main/proto")
	sourceDirs += file("src/generated/main/java")
	sourceDirs += file("src/generated/main/rsocketRpc")

	generatedSourceDirs += file('src/generated/main/java')
	generatedSourceDirs += file('src/generated/main/rsocketRpc')
  }
}

// clean generated code
clean {
  delete 'src/generated/main'
}
```

### Define a Protobuf
After you have installed Protobuf and configured Gradle you need to create a Protobuf IDL to define your service. The following is a simple Protobuf example that defines all the interaction models:
```
syntax = "proto3";

package io.rsocket.rpc.testing;

import "google/protobuf/empty.proto";

option java_package = "io.rsocket.rpc.testing.protobuf";
option java_outer_classname = "SimpleServiceProto";
option java_multiple_files = true;

service SimpleService {
  // Request / Response
  rpc RequestReply (SimpleRequest) returns (SimpleResponse) {}

  // Fire-and-Forget
  rpc FireAndForget (SimpleRequest) returns (google.protobuf.Empty) {}

  // Single Request / Streaming Response
  rpc RequestStream (SimpleRequest) returns (stream SimpleResponse) {}

  // Streaming Request / Single Response
  rpc StreamingRequestSingleResponse (stream SimpleRequest) returns (SimpleResponse) {}

  // Streaming Request / Streaming Response
  rpc StreamingRequestAndResponse (stream SimpleRequest) returns (stream SimpleResponse) {}
}

message SimpleRequest {
  string requestMessage = 1;
}

message SimpleResponse {
  string responseMessage = 1;
}
```

## Generate Files
After you have configured your Protobuf IDL, use the Protobuf compiler via the Gradle plugin to generate your files:
```
$ gradlew generateProto
```

The Protobuf compiler will generate Protobuf builders for the message, and then use the RSocket RPC compiler to generate an interface for the service defined in the IDL. It will also generate a client that implements the interface, and a server takes an implementation of the interface. This is what servers the requests. 

Using the above IDL as an example RSocket RPC will generate several class from the above example, but the ones that you need to concern yourself are:
* SimpleRequest
* SimpleResponse
* SimpleService
* SimpleServiceClient
* SimpleServiceServer

### RSocket RPC IDL Example
Here is what the simple `SimpleService` is actually and interface, and represent the service contract from the IDL. The `SimpleService` interface needs to be implemented in order to handle requests. Here is an example implementation:
```
class DefaultSimpleService implements SimpleService {
    @Override
	public Mono<Empty> fireAndForget(SimpleRequest message, ByteBuf metadata) {
	  System.out.println("got message -> " + message.getRequestMessage());
	  return Mono.just(Empty.getDefaultInstance());
	}
    
    @Override
    public Mono<SimpleResponse> requestReply(SimpleRequest message, ByteBuf metadata) {
      return Mono.fromCallable(
          () ->
              SimpleResponse.newBuilder()
                  .setResponseMessage("we got the message -> " + message.getRequestMessage())
                  .build());
    }
    
    @Override
    public Mono<SimpleResponse> streamingRequestSingleResponse(Publisher<SimpleRequest> messages, ByteBuf metadata) {
      return Flux.from(messages)
          .windowTimeout(10, Duration.ofSeconds(500))
          .take(1)
          .flatMap(Function.identity())
          .reduce(
              new ConcurrentHashMap<Character, AtomicInteger>(),
              (map, s) -> {
                char[] chars = s.getRequestMessage().toCharArray();
                for (char c : chars) {
                  map.computeIfAbsent(c, _c -> new AtomicInteger()).incrementAndGet();
                }
    
                return map;
              })
          .map(
              map -> {
                StringBuilder builder = new StringBuilder();
    
                map.forEach(
                    (character, atomicInteger) -> {
                      builder
                          .append("character -> ")
                          .append(character)
                          .append(", count -> ")
                          .append(atomicInteger.get())
                          .append("\n");
                    });
    
                String s = builder.toString();
    
                return SimpleResponse.newBuilder().setResponseMessage(s).build();
              });
    }
    
    @Override
    public Flux<SimpleResponse> requestStream(SimpleRequest message, ByteBuf metadata) {
      String requestMessage = message.getRequestMessage();
      return Flux.interval(Duration.ofMillis(200))
          .onBackpressureDrop()
          .map(i -> i + " - got message - " + requestMessage)
          .map(s -> SimpleResponse.newBuilder().setResponseMessage(s).build());
    }
    
    @Override
    public Flux<SimpleResponse> streamingRequestAndResponse(Publisher<SimpleRequest> messages, ByteBuf metadata) {
      return Flux.from(messages).flatMap(e -> requestReply(e, metadata));
    }
}
```

### RSocket RPC Server Configuration
Each generated service has a client and server implementation generated for you. After you have implemented the generated interface you need to hand the implementation to the  server. See the below example:
```
SimpleServiceServer serviceServer = new SimpleServiceServer(new DefaultSimpleService(), Optional.empty(), Optional.empty());
```

Once you have created an instance of the the server you need to configure RSocket. The 
following is a RSocket server configuration

```java
CloseableChannel closeableChannel = RSocketFactory
        .receive()
        .acceptor((setup, sendingSocket) -> Mono.just(
            new RequestHandlingRSocket(serviceServer)
        ))
        .transport(TcpServerTransport.create(8081))
        .start()
        .block();
```

#### RSocket RPC Client common Configuration
The RSocket RPC compiler generates a client as well as a server. The client implements the generated interface. You can configure the client either from an RSocket client connection, or server connection. The following shows how to  configure a initiator of a connection, typically a client, to send requests to the `SimpleService` implementation.

```java
RSocket rSocket = RSocketFactory
	.connect()
	.transport(TcpClientTransport.create(8801))
	.start()
	.block();
SimpleServiceClient serviceClient = new SimpleServiceClient(rSocket);
```

#### RSocket RPC client over Server Configuration
This configures the receiver of a connection, typically a server, to call the remote  `SimpleService`. Notice that the client is created inside the closure in the acceptor method. The method passes in a variable called `sendingSocket`. This is the RSocket that is the connection to the client. You  can make calls to the client *without* receiving requests first, or ever.
```
RSocketFactory.receive()
        .acceptor(
            (setup, sendingSocket) -> {
              SimpleServiceClient client = new SimpleServiceClient(sendingSocket);

              // Trivial example - this could also be an RSocket RPC service.
              return Mono.just(
                  new AbstractRSocket() {
                    @Override
                    public Mono<Void> fireAndForget(Payload payload) {
                      return client.fireAndForget(
                          SimpleRequest.newBuilder()
                              .setRequestMessage(payload.getDataUtf8())
                              .build());
                    }
                  });
            })
        .transport(TcpServerTransport.create(8801))
        .start()
        .block();
```

### Calling the client
Once the client is created, it can be called like any other method. Here is an example call the `streamingRequestSingleResponse` method.
```
SimpleServiceClient client = new SimpleServiceClient(rSocket);

Flux<SimpleRequest> requests =
    Flux.range(1, 11)
        .map(i -> "sending -> " + i)
        .map(s -> SimpleRequest.newBuilder().setRequestMessage(s).build());

SimpleResponse response = client.streamingRequestSingleResponse(requests).block();

System.out.println(response.getResponseMessage());
```

The above example streams in 11 items to a server. The server receives the stream, counts the most common words, and then returns a message detailing the data received.

## Next Steps

See `rsocket-rpc-examples` for more examples and use-cases.