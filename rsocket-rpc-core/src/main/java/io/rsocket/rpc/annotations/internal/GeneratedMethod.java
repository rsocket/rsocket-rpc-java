package io.rsocket.rpc.annotations.internal;

import io.rsocket.rpc.annotations.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GeneratedMethod {

  /**
   * Type of the class returned from the generated method.
   *
   * @return parameterized type of return class
   */
  Class<?> returnTypeClass();
}
