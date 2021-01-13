package io.rsocket.rpc.rsocket;

import io.rsocket.ipc.SelfRegistrable;
import io.rsocket.rpc.RSocketRpcService;

@Deprecated
public class RequestHandlingRSocket extends io.rsocket.ipc.RequestHandlingRSocket {

  public RequestHandlingRSocket(RSocketRpcService... services) {
    super();
    for (RSocketRpcService rsocketService : services) {
      withEndpoint(rsocketService);
    }
  }

  /**
   * @deprecated in favour of {@link #withService(RSocketRpcService)}
   * @param rsocketService
   */
  @Deprecated
  public void addService(RSocketRpcService rsocketService) {
    withEndpoint(rsocketService);
  }

  @Deprecated
  public RequestHandlingRSocket withService(RSocketRpcService rSocketRpcService) {
    return withEndpoint(rSocketRpcService);
  }

  @Override
  public RequestHandlingRSocket withEndpoint(SelfRegistrable selfRegistrable) {
    return (RequestHandlingRSocket) super.withEndpoint(selfRegistrable);
  }
}
