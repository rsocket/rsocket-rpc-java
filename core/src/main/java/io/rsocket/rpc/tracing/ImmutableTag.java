package io.rsocket.rpc.tracing;

import static java.util.Objects.requireNonNull;

import io.micrometer.core.lang.Nullable;
import java.util.Objects;

class ImmutableTag implements Tag {
  private String key;
  private String value;

  public ImmutableTag(String key, String value) {
    requireNonNull(key);
    requireNonNull(value);
    this.key = key;
    this.value = value;
  }

  @Override
  public String getKey() {
    return key;
  }

  @Override
  public String getValue() {
    return value;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Tag that = (Tag) o;
    return Objects.equals(key, that.getKey()) && Objects.equals(value, that.getValue());
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, value);
  }

  @Override
  public String toString() {
    return "ImmutableTag{" + "key='" + key + '\'' + ", value='" + value + '\'' + '}';
  }
}
