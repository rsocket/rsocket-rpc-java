/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.rpc.tracing;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapInjectAdapter;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.util.Logger;
import reactor.util.Loggers;
import reactor.util.context.Context;

/**
 * A trace representation of the {@link Subscriber}
 *
 * @author Stephane Maldini
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
final class SpanSubscriber<T> extends AtomicBoolean implements SpanSubscription<T> {

  private static final Logger log = Loggers.getLogger(SpanSubscriber.class);

  private final Span span;
  private final Span rootSpan;
  private final Subscriber<? super T> subscriber;
  private final Context context;
  private final Tracer tracer;
  private Subscription s;

  SpanSubscriber(
      Subscriber<? super T> subscriber,
      Context ctx,
      Tracer tracer,
      Map<String, String> tracingMetadata,
      SpanContext spanContext,
      String name,
      Tag... tags) {
    this.subscriber = subscriber;
    this.tracer = tracer;
    this.rootSpan = null;

    Tracer.SpanBuilder spanBuilder = this.tracer.buildSpan(name).asChildOf(spanContext);
    if (tags != null && tags.length > 0) {
      for (Tag tag : tags) {
        spanBuilder.withTag(tag.getKey(), tag.getValue());
      }
    }

    this.span = spanBuilder.start();

    if (tracingMetadata != null) {
      TextMapInjectAdapter adapter = new TextMapInjectAdapter(tracingMetadata);
      tracer.inject(span.context(), Format.Builtin.TEXT_MAP, adapter);
    }

    if (log.isTraceEnabled()) {
      log.trace(
          "Created span [{}], with name [{}], child of [{}]",
          this.span,
          name,
          spanContext.toString());
    }

    this.context = ctx.put(Span.class, this.span);
  }

  SpanSubscriber(
      Subscriber<? super T> subscriber,
      Context ctx,
      Tracer tracer,
      Map<String, String> tracingMetadata,
      String name,
      Tag... tags) {
    this.subscriber = subscriber;
    this.tracer = tracer;
    Span root = ctx.getOrDefault(Span.class, this.tracer.activeSpan());
    if (log.isTraceEnabled()) {
      log.trace("Span from context [{}]", root);
    }
    this.rootSpan = root;
    if (log.isTraceEnabled()) {
      log.trace("Stored context root span [{}]", this.rootSpan);
    }

    Tracer.SpanBuilder spanBuilder = this.tracer.buildSpan(name);
    if (tags != null && tags.length > 0) {
      for (Tag tag : tags) {
        spanBuilder.withTag(tag.getKey(), tag.getValue());
      }
    }

    if (root != null) {
      spanBuilder.asChildOf(root);
    }

    this.span = spanBuilder.start();

    if (tracingMetadata != null) {
      TextMapInjectAdapter adapter = new TextMapInjectAdapter(tracingMetadata);
      tracer.inject(span.context(), Format.Builtin.TEXT_MAP, adapter);
    }

    if (log.isTraceEnabled()) {
      log.trace("Created span [{}], with name [{}]", this.span, name);
    }
    this.context = ctx.put(Span.class, this.span);
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    if (log.isTraceEnabled()) {
      log.trace("On subscribe");
    }
    this.s = subscription;
    try (Scope scope = this.tracer.scopeManager().activate(span, false)) {
      scope.span().log(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()), "onSubscribe");
      if (log.isTraceEnabled()) {
        log.trace("On subscribe - span continued");
      }
      this.subscriber.onSubscribe(this);
    }
  }

  @Override
  public void request(long n) {
    if (log.isTraceEnabled()) {
      log.trace("Request");
    }
    if (log.isTraceEnabled()) {
      log.trace("Request - continued");
    }
    this.s.request(n);
    // no additional cleaning is required cause we operate on scopes
    if (log.isTraceEnabled()) {
      log.trace("Request after cleaning. Current span [{}]", this.tracer.activeSpan());
    }
  }

  @Override
  public void cancel() {
    try (Scope scope = this.tracer.scopeManager().activate(span, false)) {
      scope.span().log(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()), "cancel");
      if (log.isTraceEnabled()) {
        log.trace("Cancel");
      }
      this.s.cancel();
    } finally {
      cleanup();
    }
  }

  @Override
  public void onNext(T o) {
    this.subscriber.onNext(o);
  }

  @Override
  public void onError(Throwable throwable) {
    try (Scope scope = this.tracer.scopeManager().activate(span, false)) {
      scope.span().log(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()), "onError");
    } finally {
      cleanup();
    }
  }

  @Override
  public void onComplete() {
    try (Scope scope = this.tracer.scopeManager().activate(span, false)) {
      scope.span().log(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()), "onComplete");
      this.subscriber.onComplete();
    } finally {
      cleanup();
    }
  }

  void cleanup() {
    if (compareAndSet(false, true)) {
      if (log.isTraceEnabled()) {
        log.trace("Cleaning up");
      }
      this.span.finish();
      if (log.isTraceEnabled()) {
        log.trace("Span closed");
      }
      if (this.rootSpan != null) {
        this.rootSpan.finish();
        if (log.isTraceEnabled()) {
          log.trace("Closed root span");
        }
      }
    }
  }

  @Override
  public Context currentContext() {
    return this.context;
  }
}
