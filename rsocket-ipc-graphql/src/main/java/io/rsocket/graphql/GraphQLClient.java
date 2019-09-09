package io.rsocket.graphql;

import io.rsocket.RSocket;
import io.rsocket.ipc.Client;
import io.rsocket.ipc.Functions;
import io.rsocket.ipc.Marshaller;
import io.rsocket.ipc.Unmarshaller;
import java.util.Objects;

@SuppressWarnings({"unchecked", "unused"})
public final class GraphQLClient {
  GraphQLClient() {}

  public interface R {
    P rsocket(RSocket rSocket);
  }

  public interface P {
    U marshall(Marshaller<GraphQLRequest> marshaller);
  }

  public interface U {
    <T> C<T> unmarshall(Unmarshaller<T> unmarshaller);
  }

  public interface C<T> {
    Query<T> query();

    Mutate<T> mutate();

    Subscription<T> subscription();
  }

  private static class Builder<T> implements R, P, U, C<T> {
    private final String service;
    private Marshaller<GraphQLRequest> marshaller;
    private Unmarshaller unmarshaller;
    private RSocket rsocket;

    private Builder(final String service) {
      this.service = service;
    }

    @Override
    public U marshall(Marshaller<GraphQLRequest> marshaller) {
      this.marshaller = marshaller;
      return this;
    }

    @Override
    public <T> C<T> unmarshall(Unmarshaller<T> unmarshaller) {
      this.unmarshaller = unmarshaller;
      return (C<T>) this;
    }

    @Override
    public P rsocket(RSocket rsocket) {
      this.rsocket = Objects.requireNonNull(rsocket);
      return this;
    }

    private <T> Client<GraphQLRequest, T> client() {
      Objects.requireNonNull(service);
      Objects.requireNonNull(rsocket);
      Objects.requireNonNull(marshaller);
      Objects.requireNonNull(unmarshaller);

      return Client.service(service).rsocket(rsocket).marshall(marshaller).unmarshall(unmarshaller);
    }

    @Override
    public Query<T> query() {
      Functions.RequestResponse<GraphQLRequest, T> query =
          (Functions.RequestResponse<GraphQLRequest, T>) client().requestResponse("Query");

      return query::apply;
    }

    @Override
    public Mutate<T> mutate() {
      Functions.RequestResponse<GraphQLRequest, T> mutate =
          (Functions.RequestResponse<GraphQLRequest, T>) client().requestResponse("Mutate");

      return mutate::apply;
    }

    @Override
    public Subscription<T> subscription() {
      Functions.RequestStream<GraphQLRequest, T> subscription =
          (Functions.RequestStream<GraphQLRequest, T>) client().requestStream("Subscription");

      return subscription::apply;
    }
  }

  public static R service(String service) {
    return new Builder(Objects.requireNonNull(service));
  }

  @FunctionalInterface
  interface Query<T> extends Functions.RequestResponse<GraphQLRequest, T> {}

  @FunctionalInterface
  interface Mutate<T> extends Functions.RequestResponse<GraphQLRequest, T> {}

  @FunctionalInterface
  interface Subscription<T> extends Functions.RequestStream<GraphQLRequest, T> {}
}
