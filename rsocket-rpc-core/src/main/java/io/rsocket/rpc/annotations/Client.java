package io.rsocket.rpc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Annotation that identifies RSocketRpc client implementations. */
@Annotation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Client {

  /**
   * The group name that the client will be communicating with.
   *
   * @return RSocketRpc group name
   */
  String group();

  /**
   * The destination name that the client will be communicating with. If not specified, requests by
   * this client will be load balanced across all destinations within the specified group.
   *
   * @return RSocketRpc destination name
   */
  String destination() default "";
}
