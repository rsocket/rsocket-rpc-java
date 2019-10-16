package io.rsocket.graphql;

import graphql.GraphQLError;
import java.util.List;

public class GraphQLErrorException extends Exception {
  private static final long serialVersionUID = 1;

  private GraphQLErrorException(String message) {
    super(message);
  }

  public static GraphQLErrorException from(List<GraphQLError> exceptions) {
    StringBuilder errorMessages = new StringBuilder("Received the following errors: \n");

    for (GraphQLError g : exceptions) {
      errorMessages
          .append("Error Type: ")
          .append(g.getErrorType())
          .append(", Message: " + g.getMessage())
          .append("\n");
    }

    return new GraphQLErrorException(errorMessages.toString());
  }
}
