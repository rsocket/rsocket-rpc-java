package io.rsocket.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.ExecutionId;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLSchema;
import io.netty.buffer.ByteBuf;
import java.util.concurrent.CompletableFuture;
import org.dataloader.DataLoaderRegistry;

class Util {
  private Util() {}

  static CompletableFuture<ExecutionResult> executeGraphQLRequest(
      GraphQLRequest request,
      ByteBuf byteBuf,
      DataLoaderRegistry registry,
      GraphQLSchema graphQLSchema,
      Instrumentation instrumentation) {
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

    return GraphQL.newGraphQL(graphQLSchema)
        .instrumentation(instrumentation)
        .build()
        .executeAsync(executionInput);
  }
}
