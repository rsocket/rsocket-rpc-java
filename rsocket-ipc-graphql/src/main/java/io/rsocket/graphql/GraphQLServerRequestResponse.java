package io.rsocket.graphql;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLSchema;
import io.netty.buffer.ByteBuf;
import io.rsocket.ipc.Functions;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.dataloader.DataLoaderRegistry;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@SuppressWarnings("unchecked")
class GraphQLServerRequestResponse implements Functions.RequestResponse<GraphQLRequest, Object> {
  private final DataLoaderRegistry registry;
  private final Instrumentation instrumentation;
  private final GraphQLSchema graphQLSchema;

  GraphQLServerRequestResponse(
      DataLoaderRegistry registry, Instrumentation instrumentation, GraphQLSchema graphQLSchema) {
    this.registry = registry;
    this.instrumentation = instrumentation;
    this.graphQLSchema = graphQLSchema;
  }

  @Override
  public Mono<Object> apply(GraphQLRequest request, ByteBuf byteBuf) {
    try {
      CompletableFuture<ExecutionResult> result =
          Util.executeGraphQLRequest(request, byteBuf, registry, graphQLSchema, instrumentation);

      return Mono.fromFuture(result)
          .flatMap(
              executionResult -> {
                List<GraphQLError> errors = executionResult.getErrors();

                if (!errors.isEmpty()) {
                  return Mono.error(GraphQLErrorException.from(errors));
                }

                if (!executionResult.isDataPresent()) {
                  return Mono.empty();
                }

                Object r = executionResult.getData();

                if (r == null) {
                  return Mono.error(new NullPointerException("result data was null"));
                }

                if (r instanceof Publisher) {
                  return Mono.from((Publisher) r);
                } else {
                  return Mono.just(r);
                }
              });

    } catch (Throwable t) {
      return Mono.error(t);
    }
  }
}
