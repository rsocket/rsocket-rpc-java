package io.rsocket.rpc.metrics;

import static reactor.core.Fuseable.ASYNC;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.Fuseable.QueueSubscription;
import reactor.core.publisher.Operators;
import reactor.util.annotation.Nullable;
import reactor.util.context.Context;

public class MetricsFuseableSubscriber<T> extends AtomicBoolean
    implements QueueSubscription<T>, CoreSubscriber<T> {
  private final CoreSubscriber<? super T> actual;
  private final Counter next, complete, error, cancelled;
  private final Timer timer;

  private QueueSubscription<T> s;
  private int sourceMode;

  private long start;

  MetricsFuseableSubscriber(
      CoreSubscriber<? super T> actual,
      Counter next,
      Counter complete,
      Counter error,
      Counter cancelled,
      Timer timer) {
    this.actual = actual;
    this.next = next;
    this.complete = complete;
    this.error = error;
    this.cancelled = cancelled;
    this.timer = timer;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void onSubscribe(Subscription s) {
    if (Operators.validate(this.s, s)) {
      this.s = (QueueSubscription<T>) s;
      this.start = System.nanoTime();

      actual.onSubscribe(this);
    }
  }

  @Override
  public void onNext(T t) {
    if (sourceMode == ASYNC) {
      actual.onNext(null);
    } else {
      next.increment();
      actual.onNext(t);
    }
  }

  @Override
  public void onError(Throwable t) {
    if (compareAndSet(false, true)) {
      error.increment();
      timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
    actual.onError(t);
  }

  @Override
  public void onComplete() {
    if (compareAndSet(false, true)) {
      complete.increment();
      timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
    actual.onComplete();
  }

  @Override
  public void request(long n) {
    s.request(n);
  }

  @Override
  public void cancel() {
    if (compareAndSet(false, true)) {
      cancelled.increment();
      timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
    s.cancel();
  }

  @Override
  public Context currentContext() {
    return actual.currentContext();
  }

  @Override
  public int requestFusion(int requestedMode) {
    int m;
    if ((requestedMode & Fuseable.THREAD_BARRIER) != 0) {
      return Fuseable.NONE;
    } else {
      m = s.requestFusion(requestedMode);
    }
    sourceMode = m;
    return m;
  }

  @Override
  @Nullable
  public T poll() {
    T v = s.poll();
    if (v != null) {
      next.increment();
      return v;
    }
    return null;
  }

  @Override
  public boolean isEmpty() {
    return s.isEmpty();
  }

  @Override
  public void clear() {
    s.clear();
  }

  @Override
  public int size() {
    return s.size();
  }
}
