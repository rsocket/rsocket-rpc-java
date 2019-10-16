package io.rsocket.graphql;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentracing.Tracer;
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
    M rsocket(RSocket rSocket);
  }

  public interface M {
    T noMeterRegistry();

    T meterRegistry(MeterRegistry registry);
  }

  public interface T {
    P noTracer();

    P tracer(Tracer tracer);
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

  private static class Builder<O> implements R, P, U, C<O>, M, T {
    private final String service;
    private Marshaller<GraphQLRequest> marshaller;
    private Unmarshaller unmarshaller;
    private RSocket rsocket;
    private MeterRegistry meterRegistry;
    private Tracer tracer;

    private Builder(final String service) {
      this.service = service;
    }

    @Override
    public U marshall(Marshaller<GraphQLRequest> marshaller) {
      this.marshaller = marshaller;
      return this;
    }

    @Override
    public <O> C<O> unmarshall(Unmarshaller<O> unmarshaller) {
      this.unmarshaller = unmarshaller;
      return (C<O>) this;
    }

    @Override
    public T noMeterRegistry() {
      return this;
    }

    @Override
    public T meterRegistry(MeterRegistry meterRegistry) {
      this.meterRegistry = meterRegistry;
      return this;
    }

    @Override
    public P noTracer() {
      return this;
    }

    @Override
    public P tracer(Tracer tracer) {
      this.tracer = tracer;
      return this;
    }

    @Override
    public M rsocket(RSocket rsocket) {
      this.rsocket = Objects.requireNonNull(rsocket);
      return this;
    }

    private <O> Client<GraphQLRequest, O> client() {
      Objects.requireNonNull(service);
      Objects.requireNonNull(rsocket);
      Objects.requireNonNull(marshaller);
      Objects.requireNonNull(unmarshaller);

      Client.M rsocket = Client.service(service).rsocket(this.rsocket);

      Client.T t;
      if (meterRegistry != null) {
        t = rsocket.meterRegistry(meterRegistry);
      } else {
        t = rsocket.noMeterRegistry();
      }

      Client.P p;
      if (tracer != null) {
        p = t.tracer(this.tracer);
      } else {
        p = t.noTracer();
      }

      return p.marshall(marshaller).unmarshall(unmarshaller);
    }

    @Override
    public Query<O> query() {
      Functions.RequestResponse query = client().requestResponse("Query");

      return query::apply;
    }

    @Override
    public Mutate<O> mutate() {
      Functions.RequestResponse mutate = client().requestResponse("Mutate");

      return mutate::apply;
    }

    @Override
    public Subscription<O> subscription() {
      Functions.RequestStream subscription = client().requestStream("Subscription");

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
