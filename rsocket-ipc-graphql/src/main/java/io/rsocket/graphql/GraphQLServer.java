package io.rsocket.graphql;

import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import io.rsocket.ipc.IPCRSocket;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Server;
import io.rsocket.ipc.Unmarshaller;
import java.util.Objects;
import org.dataloader.DataLoaderRegistry;

@SuppressWarnings({"unchecked", "unused"})
public final class GraphQLServer {
  GraphQLServer() {}

  public interface P {
    <O> U<O> marshall(Marshaller<O> marshaller);
  }

  public interface U<O> {
    <O> D<O> unmarshall(Unmarshaller<GraphQLRequest> unmarshaller);
  }

  public interface D<O> {
    I<O> dataLoadRegister(DataLoaderRegistry dataLoadRegistry);

    I<O> noDataLoadRegister();
  }

  public interface I<O> {
    S<O> instrumentation(Instrumentation instrumentation);

    S<O> defaultInstrumentation();
  }

  public interface S<O> {
    R<O> schema(GraphQLSchema graphQLSchema);
  }

  public interface R<O> {
    T<O> readOnlySchema(GraphQLSchema graphQLSchema);

    T<O> noReadOnlySchema();
  }

  public interface T<O> {
    IPCRSocket rsocket();
  }

  private static class Builder implements P, U, D, I, S, R, T {
    private final String service;
    private Unmarshaller<GraphQLRequest> unmarshaller;
    private Marshaller marshaller;
    private DataLoaderRegistry dataLoadRegistry;
    private Instrumentation instrumentation = SimpleInstrumentation.INSTANCE;
    private GraphQLSchema schema;
    private GraphQLSchema readOnlySchema;

    private Builder(String service) {
      this.service = service;
    }

    @Override
    public <O> U<O> marshall(Marshaller<O> marshaller) {
      this.marshaller = Objects.requireNonNull(marshaller);
      return this;
    }

    @Override
    public D unmarshall(Unmarshaller unmarshaller) {
      this.unmarshaller = Objects.requireNonNull(unmarshaller);
      return this;
    }

    @Override
    public I dataLoadRegister(DataLoaderRegistry dataLoadRegistry) {
      this.dataLoadRegistry = Objects.requireNonNull(dataLoadRegistry);
      ;
      return this;
    }

    @Override
    public I noDataLoadRegister() {
      return this;
    }

    @Override
    public S defaultInstrumentation() {
      return this;
    }

    @Override
    public T noReadOnlySchema() {
      readOnlySchema = GraphQLSchema.newSchema(schema).mutation((GraphQLObjectType) null).build();
      return this;
    }

    @Override
    public IPCRSocket rsocket() {
      return Server.service(service)
          .marshall(marshaller)
          .unmarshall(unmarshaller)
          .requestResponse(
              "Query",
              new GraphQLServerRequestResponse(dataLoadRegistry, instrumentation, readOnlySchema))
          .requestResponse(
              "Mutation",
              new GraphQLServerRequestResponse(dataLoadRegistry, instrumentation, schema))
          .requestStream(
              "Subscription",
              new GraphQLServerRequestStream(dataLoadRegistry, instrumentation, schema))
          .rsocket();
    }

    @Override
    public Builder instrumentation(Instrumentation instrumentation) {
      this.instrumentation = Objects.requireNonNull(instrumentation);
      return this;
    }

    @Override
    public Builder schema(GraphQLSchema schema) {
      this.schema = Objects.requireNonNull(schema);
      return this;
    }

    @Override
    public Builder readOnlySchema(GraphQLSchema readOnlySchema) {
      this.readOnlySchema = Objects.requireNonNull(readOnlySchema);
      return this;
    }
  }

  public static P service(String service) {
    return new Builder(Objects.requireNonNull(service));
  }
}
