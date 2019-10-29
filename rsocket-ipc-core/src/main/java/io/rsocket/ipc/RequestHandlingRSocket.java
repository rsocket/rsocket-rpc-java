package io.rsocket.ipc;

import io.opentracing.Tracer;
import io.rsocket.ResponderRSocket;
import java.util.concurrent.ConcurrentHashMap;

public class RequestHandlingRSocket extends RoutingServerRSocket implements ResponderRSocket {

  public RequestHandlingRSocket() {
    super(
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>());
  }

  public RequestHandlingRSocket(Tracer tracer) {
    super(
        tracer,
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>());
  }

  public RequestHandlingRSocket(MetadataDecoder decoder) {
    super(
        decoder,
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>());
  }

  public RequestHandlingRSocket withEndpoint(SelfRegistrable selfRegistrable) {
    selfRegistrable.selfRegister(
        fireAndForgetRegistry,
        requestResponseRegistry,
        requestStreamRegistry,
        requestChannelRegistry);
    return this;
  }
}
