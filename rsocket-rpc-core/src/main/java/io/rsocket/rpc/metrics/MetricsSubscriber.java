package io.rsocket.rpc.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

public class MetricsSubscriber<T> extends AtomicBoolean implements Subscription, CoreSubscriber<T> {
  private final CoreSubscriber<? super T> actual;
  private final Counter next, complete, error, cancelled;
  private final Timer timer;

  private Subscription s;
  private long start;

  MetricsSubscriber(
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
  public void onSubscribe(Subscription s) {
    if (Operators.validate(this.s, s)) {
      this.s = s;
      this.start = System.nanoTime();

      actual.onSubscribe(this);
    }
  }

  @Override
  public void onNext(T t) {
    next.increment();
    actual.onNext(t);
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
}
