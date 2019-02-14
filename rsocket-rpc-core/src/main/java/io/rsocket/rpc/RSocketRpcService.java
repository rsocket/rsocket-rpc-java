package io.rsocket.rpc;

import io.rsocket.ResponderRSocket;

public interface RSocketRpcService extends ResponderRSocket {
  String getService();
}
