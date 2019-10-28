package io.rsocket.rpc.tracing;

import io.opentracing.SpanContext;
import java.util.Map;

public class SimpleSpanContext implements SpanContext {
  final Map<String, String> baggage;

  public SimpleSpanContext(Map<String, String> baggage) {
    this.baggage = baggage;
  }

  @Override
  public Iterable<Map.Entry<String, String>> baggageItems() {
    return baggage.entrySet();
  }
}
