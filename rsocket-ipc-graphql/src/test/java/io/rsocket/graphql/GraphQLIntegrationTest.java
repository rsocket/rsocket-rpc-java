package io.rsocket.graphql;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.core.RSocketServer;
import io.rsocket.ipc.IPCRSocket;
import io.rsocket.ipc.RequestHandlingRSocket;
import io.rsocket.ipc.Unmarshaller;
import io.rsocket.ipc.encoders.DefaultMetadataEncoder;
import io.rsocket.ipc.marshallers.Json;
import io.rsocket.transport.local.LocalClientTransport;
import io.rsocket.transport.local.LocalServerTransport;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

public class GraphQLIntegrationTest {
  @Test
  public void testQuery() throws Exception {
    RequestHandlingRSocket requestHandler = new RequestHandlingRSocket();

    RSocketServer.create()
        .acceptor((setup, sendingSocket) -> Mono.just(requestHandler))
        .bindNow(LocalServerTransport.create("testQuery"));

    RSocket rsocket =
        RSocketConnector.connectWith(LocalClientTransport.create("testQuery")).block();

    String query =
        "{\n"
            + "    bookById(id: \"book-1\") {\n"
            + "        id\n"
            + "        name\n"
            + "        pageCount\n"
            + "        author {\n"
            + "            firstName\n"
            + "            lastName\n"
            + "        }\n"
            + "    }\n"
            + "}";

    IPCRSocket service =
        GraphQLServer.service("books")
            .noMeterRegistry()
            .noTracer()
            .marshall(Json.marshaller(Object.class))
            .unmarshall(Json.unmarshaller(GraphQLRequest.class))
            .noDataLoadRegister()
            .defaultInstrumentation()
            .schema(getGraphQLSchema())
            .noReadOnlySchema()
            .toIPCRSocket();

    requestHandler.withEndpoint(service);

    GraphQLClient.Query bookQuery =
        GraphQLClient.service("books")
            .rsocket(rsocket)
            .customMetadataEncoder(new DefaultMetadataEncoder(ByteBufAllocator.DEFAULT))
            .noMeterRegistry()
            .noTracer()
            .marshall(Json.marshaller(GraphQLRequest.class))
            .unmarshall(unmarshaller())
            .query();

    GraphQLRequest request = new GraphQLRequest(query, new HashMap<>(), "");
    GraphQLDataFetchers.Book book = (GraphQLDataFetchers.Book) bookQuery.apply(request).block();
    System.out.println(book);
  }

  private static Unmarshaller<GraphQLDataFetchers.Book> unmarshaller() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new AfterburnerModule());
    return byteBuf -> {
      try {
        InputStream byteBufInputStream = new ByteBufInputStream(byteBuf);
        Map map = mapper.readValue(byteBufInputStream, Map.class);
        Object bookById = map.get("bookById");
        return mapper.convertValue(bookById, GraphQLDataFetchers.Book.class);
      } catch (Exception e) {
        throw Exceptions.propagate(e);
      }
    };
  }

  private static GraphQLSchema getGraphQLSchema() throws Exception {
    SchemaParser schemaParser = new SchemaParser();
    SchemaGenerator schemaGenerator = new SchemaGenerator();

    URL resource = Thread.currentThread().getContextClassLoader().getResource("schema.graphqls");
    Path path = Paths.get(resource.toURI());
    String s = read(path);

    TypeDefinitionRegistry registry = schemaParser.parse(s);

    RuntimeWiring wiring =
        RuntimeWiring.newRuntimeWiring()
            .type(
                newTypeWiring("Query")
                    .dataFetcher("bookById", GraphQLDataFetchers.getBookByIdDataFetcher()))
            .type(
                newTypeWiring("Book")
                    .dataFetcher("author", GraphQLDataFetchers.getAuthorDataFetcher()))
            .build();

    return schemaGenerator.makeExecutableSchema(registry, wiring);
  }

  private static String read(Path path) {
    try {
      File file = path.toFile();
      FileInputStream fis = new FileInputStream(file);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();

      int b = 0;
      while ((b = fis.read()) != -1) {
        bos.write(b);
      }

      return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
