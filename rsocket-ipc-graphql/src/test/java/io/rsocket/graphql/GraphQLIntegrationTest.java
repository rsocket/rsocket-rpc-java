package io.rsocket.graphql;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.netty.buffer.ByteBufInputStream;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.ipc.IPCRSocket;
import io.rsocket.ipc.Unmarshaller;
import io.rsocket.ipc.marshallers.Json;
import io.rsocket.rpc.rsocket.RequestHandlingRSocket;
import io.rsocket.transport.local.LocalClientTransport;
import io.rsocket.transport.local.LocalServerTransport;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
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

    RSocketFactory.receive()
        .errorConsumer(Throwable::printStackTrace)
        .acceptor((setup, sendingSocket) -> Mono.just(requestHandler))
        .transport(LocalServerTransport.create("testQuery"))
        .start()
        .block();

    RSocket rsocket =
        RSocketFactory.connect()
            .errorConsumer(Throwable::printStackTrace)
            .transport(LocalClientTransport.create("testQuery"))
            .start()
            .block();

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
            .marshall(Json.marshaller(Object.class))
            .unmarshall(Json.unmarshaller(GraphQLRequest.class))
            .noDataLoadRegister()
            .defaultInstrumentation()
            .schema(getGraphQLSchema())
            .noReadOnlySchema()
            .rsocket();

    requestHandler.withService(service);

    GraphQLClient.Query<GraphQLDataFetchers.Book> bookQuery =
        GraphQLClient.service("books")
            .rsocket(rsocket)
            .marshall(Json.marshaller(GraphQLRequest.class))
            .unmarshall(unmarshaller())
            .query();

    GraphQLRequest request = new GraphQLRequest(query, new HashMap<>(), "");
    GraphQLDataFetchers.Book book = bookQuery.apply(request).block();
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
    String s = Files.readString(path);

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
}
