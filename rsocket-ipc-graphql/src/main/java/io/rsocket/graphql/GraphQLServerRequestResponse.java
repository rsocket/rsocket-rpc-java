package io.rsocket.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.execution.ExecutionId;
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
      ExecutionInput.Builder builder =
          ExecutionInput.newExecutionInput()
              .query(request.getQuery())
              .operationName(request.getOperationName())
              .variables(request.getVariables())
              .context(byteBuf)
              .executionId(ExecutionId.generate());

      if (registry != null) {
        builder.dataLoaderRegistry(registry);
      }

      ExecutionInput executionInput = builder.build();

      CompletableFuture<ExecutionResult> result =
          GraphQL.newGraphQL(graphQLSchema)
              .instrumentation(instrumentation)
              .build()
              .executeAsync(executionInput);

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
                  if (r instanceof Mono) {
                    return (Mono) r;

                  } else {
                    return Mono.error(
                        new IllegalStateException(
                            "if data is a publisher it must be assignable to a mono for request/reply interactions"));
                  }
                } else {
                  return Mono.just(r);
                }
              });

    } catch (Throwable t) {
      return Mono.error(t);
    }
  }
}
