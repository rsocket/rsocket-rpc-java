package io.rsocket.rpc.frames;

/** */
public enum FrameType {
  UNDEFINED(0x00, false),
  BROKER_SETUP(0x01, false),
  DESTINATION_SETUP(0x02, false),
  DESTINATION(0x03, true),
  GROUP(0x04, false),
  BROADCAST(0x05, true),
  SHARD(0x06, false);

  private static FrameType[] typesById;

  private final int id;
  private final boolean hasDestination;

  /** Index types by id for indexed lookup. */
  static {
    int max = 0;

    for (FrameType t : values()) {
      max = Math.max(t.id, max);
    }

    typesById = new FrameType[max + 1];

    for (FrameType t : values()) {
      typesById[t.id] = t;
    }
  }

  FrameType(int id, boolean hasDestination) {
    this.id = id;
    this.hasDestination = hasDestination;
  }

  public int getEncodedType() {
    return id;
  }

  public boolean hasDestination() {
    return hasDestination;
  }

  public static FrameType from(int id) {
    return typesById[id];
  }
}
