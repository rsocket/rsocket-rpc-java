package io.rsocket.rpc.annotations.internal;

import io.rsocket.rpc.annotations.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that identifies RSocket RPC generated services and stores metadata that can be used by
 * dependency injection frameworks and custom annotation processors.
 */
@Annotation
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Generated {

  /**
   * Type of the generated RSocketRpc resource.
   *
   * @return type of generated resource
   */
  ResourceType type();

  /**
   * Class of the RSocketRpc service hosted by the annotated class.
   *
   * @return RSocketRpc service class
   */
  Class<?> idlClass();
}
