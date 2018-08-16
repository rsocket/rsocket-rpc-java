package io.rsocket.rpc.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.netty.buffer.Unpooled;
import io.rsocket.rpc.metrics.om.*;
import io.rsocket.rpc.metrics.om.Meter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

public class MetricsExporter implements Disposable, Runnable {
  private final Logger logger = LoggerFactory.getLogger(MetricsExporter.class);
  private final MetricsSnapshotHandler handler;
  private final MeterRegistry registry;
  private final Duration exportFrequency;
  private final int batchSize;
  private volatile Disposable disposable;

  public MetricsExporter(
      MetricsSnapshotHandler handler,
      MeterRegistry registry,
      Duration exportFrequency,
      int batchSize) {
    this.handler = handler;
    this.registry = registry;
    this.exportFrequency = exportFrequency;
    this.batchSize = batchSize;
  }

  private static final String round(double percentile) {
    double roundOff = (double) Math.round(percentile * 10_000.0) / 10_000.0;
    return String.valueOf(roundOff);
  }

  private Flux<MetricsSnapshot> getMetricsSnapshotStream() {
    return Flux.fromIterable(registry.getMeters())
        .window(batchSize)
        .flatMap(
            meters ->
                meters
                    .groupBy(
                        meter -> {
                          if (meter instanceof Timer) {
                            return true;
                          } else {
                            return false;
                          }
                        })
                    .flatMap(
                        grouped -> {
                          if (grouped.key()) {
                            return grouped.reduce(
                                MetricsSnapshot.newBuilder(),
                                (builder, meter) -> {
                                  Timer timer = (Timer) meter;
                                  List<Meter> convert = convert(timer);
                                  builder.addAllMeters(convert);
                                  return builder;
                                });
                          } else {
                            return grouped.reduce(
                                MetricsSnapshot.newBuilder(),
                                (builder, meter) -> {
                                  Meter convert = convert(meter);
                                  builder.addMeters(convert);
                                  return builder;
                                });
                          }
                        })
                    .map(MetricsSnapshot.Builder::build));
  }

  private List<Meter> convert(Timer timer) {
    List<Meter> meters = new ArrayList<>();
    HistogramSnapshot snapshot = timer.takeSnapshot();

    io.micrometer.core.instrument.Meter.Id id = timer.getId();
    io.micrometer.core.instrument.Meter.Type type = id.getType();

    List<MeterTag> meterTags =
        StreamSupport.stream(id.getTags().spliterator(), false)
            .map(tag -> MeterTag.newBuilder().setKey(tag.getKey()).setValue(tag.getValue()).build())
            .collect(Collectors.toList());

    ValueAtPercentile[] valueAtPercentiles = snapshot.percentileValues();
    for (ValueAtPercentile percentile : valueAtPercentiles) {
      Meter.Builder meterBuilder = Meter.newBuilder();
      double value = percentile.value(TimeUnit.NANOSECONDS);
      MeterTag tag =
          MeterTag.newBuilder()
              .setKey("percentile")
              .setValue(round(percentile.percentile()))
              .build();
      MeterId.Builder idBuilder = MeterId.newBuilder();
      idBuilder.setName(id.getName());
      idBuilder.addAllTag(meterTags);
      idBuilder.setType(convert(type));
      if (id.getDescription() != null) {
        idBuilder.setDescription(id.getDescription());
      }
      idBuilder.setBaseUnit("nanoseconds");
      idBuilder.addTag(tag);

      meterBuilder.setId(idBuilder);
      meterBuilder.addMeasure(
          MeterMeasurement.newBuilder().setValue(value).setStatistic(MeterStatistic.DURATION));
      Meter meter = meterBuilder.build();
      meters.add(meter);
    }

    Meter convert = convert((io.micrometer.core.instrument.Meter) timer);
    meters.add(convert);

    return meters;
  }

  private Meter convert(io.micrometer.core.instrument.Meter meter) {
    Meter.Builder meterBuilder = Meter.newBuilder();
    MeterId.Builder idBuilder = MeterId.newBuilder();

    io.micrometer.core.instrument.Meter.Id id = meter.getId();
    io.micrometer.core.instrument.Meter.Type type = id.getType();

    for (Tag tag : id.getTags()) {
      idBuilder.addTag(MeterTag.newBuilder().setKey(tag.getKey()).setValue(tag.getValue()));
    }

    idBuilder.setName(id.getName());
    idBuilder.setType(convert(type));
    if (id.getDescription() != null) {
      idBuilder.setDescription(id.getDescription());
    }
    if (id.getBaseUnit() != null) {
      idBuilder.setBaseUnit(id.getBaseUnit());
    }

    meterBuilder.setId(idBuilder);

    for (Measurement measurement : meter.measure()) {
      meterBuilder.addMeasure(
          MeterMeasurement.newBuilder()
              .setValue(measurement.getValue())
              .setStatistic(convert(measurement.getStatistic())));
    }

    return meterBuilder.build();
  }

  private MeterType convert(io.micrometer.core.instrument.Meter.Type type) {
    switch (type) {
      case GAUGE:
        return MeterType.GAUGE;
      case TIMER:
        return MeterType.TIMER;
      case COUNTER:
        return MeterType.COUNTER;
      case LONG_TASK_TIMER:
        return MeterType.LONG_TASK_TIMER;
      case DISTRIBUTION_SUMMARY:
        return MeterType.DISTRIBUTION_SUMMARY;
      case OTHER:
        return MeterType.OTHER;
      default:
        throw new IllegalStateException("unknown type " + type.name());
    }
  }

  private MeterStatistic convert(Statistic statistic) {
    switch (statistic) {
      case MAX:
        return MeterStatistic.MAX;
      case COUNT:
        return MeterStatistic.COUNT;
      case TOTAL:
        return MeterStatistic.TOTAL;
      case VALUE:
        return MeterStatistic.VALUE;
      case UNKNOWN:
        return MeterStatistic.UNKNOWN;
      case DURATION:
        return MeterStatistic.DURATION;
      case TOTAL_TIME:
        return MeterStatistic.TOTAL_TIME;
      case ACTIVE_TASKS:
        return MeterStatistic.ACTIVE_TASKS;
      default:
        throw new IllegalStateException("unknown type " + statistic.name());
    }
  }

  private void recordClockSkew(Skew skew) {}

  @Override
  public void dispose() {
    Disposable d;
    synchronized (this) {
      d = disposable;
      disposable = null;
    }
    d.dispose();
  }

  @Override
  public boolean isDisposed() {
    if (disposable != null) {
      return false;
    } else {
      return disposable.isDisposed();
    }
  }

  @Override
  public void run() {
    synchronized (this) {
      if (disposable != null) {
        return;
      }
    }

    this.disposable =
        Flux.interval(exportFrequency)
            .onBackpressureDrop()
            .concatMap(
                l ->
                    handler
                        .streamMetrics(getMetricsSnapshotStream(), Unpooled.EMPTY_BUFFER)
                        .doOnNext(this::recordClockSkew))
            .doOnError(throwable -> logger.debug("error streaming metrics", throwable))
            .retry()
            .subscribe();
  }
}
