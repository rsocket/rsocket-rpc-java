package io.rsocket.ipc.tracing;

public interface Tag {
  static Tag of(String key, String value) {
    return new ImmutableTag(key, value);
  }

  String getKey();

  String getValue();
}
