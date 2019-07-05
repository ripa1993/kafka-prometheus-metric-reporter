package dev.ripaz.kpr

import java.util
import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.LazyLogging
import com.yammer.metrics.core._
import com.yammer.metrics.stats.Snapshot
import io.prometheus.client.Collector
import io.prometheus.client.Collector.{MetricFamilySamples, Type}

import scala.collection.JavaConverters._

class YammerExports(val registry: MetricsRegistry) extends Collector with Collector.Describable with LazyLogging {
  private var sampleBuilder: SampleBuilder = new DefaultSampleBuilder

  private[kpr] def fromCounter(metricName: MetricName, counter: Counter) = {
    val sample = sampleBuilder.createSample(
      makeMetricName(metricName),
      "",
      makeLabelNames(metricName).asJava,
      makeLabelValues(metricName).asJava,
      counter.count.toDouble
    )
    new Collector.MetricFamilySamples(sample.name, Type.GAUGE, getHelpMessage(metricName.getName, counter), util.Arrays.asList(sample))
  }

  private[kpr] def fromGauge(metricName: MetricName, gauge: Gauge[_]): Collector.MetricFamilySamples = {

    logger.debug("mbeanName: " + metricName.getMBeanName)
    logger.debug(s"name=${metricName.getName} group=${metricName.getGroup} scope=${metricName.getScope} type=${metricName.getType}")

    val obj = gauge.value()
    var value = .0
    obj match {
      case number: Number => value = number.doubleValue
      case bool: Boolean  => value = if (bool) 1 else 0
      case _ =>
        logger.trace(
          String.format(
            "Invalid type for Gauge %s: %s",
            Collector.sanitizeMetricName(metricName.getName),
            if (obj == null) "null"
            else obj.getClass.getName
          )
        )
        return null
    }
    val sample =
      sampleBuilder.createSample(makeMetricName(metricName), "", makeLabelNames(metricName).asJava, makeLabelValues(metricName).asJava, value)
    new Collector.MetricFamilySamples(sample.name, Type.GAUGE, getHelpMessage(metricName.getName, gauge), util.Arrays.asList(sample))
  }

  private[kpr] def fromSnapshotAndCount(metricName: MetricName, snapshot: Snapshot, count: Long, factor: Double, helpMessage: String) = {
    val samples = util.Arrays.asList(
      sampleBuilder
        .createSample(makeMetricName(metricName), "", util.Arrays.asList("quantile"), util.Arrays.asList("0.5"), snapshot.getMedian * factor),
      sampleBuilder.createSample(
        makeMetricName(metricName),
        "",
        (List("quantile") ++ makeLabelNames(metricName)).asJava,
        (List("0.75") ++ makeLabelValues(metricName)).asJava,
        snapshot.get75thPercentile * factor
      ),
      sampleBuilder.createSample(
        makeMetricName(metricName),
        "",
        (List("quantile") ++ makeLabelNames(metricName)).asJava,
        (List("0.95") ++ makeLabelValues(metricName)).asJava,
        snapshot.get95thPercentile * factor
      ),
      sampleBuilder.createSample(
        makeMetricName(metricName),
        "",
        (List("quantile") ++ makeLabelNames(metricName)).asJava,
        (List("0.98") ++ makeLabelValues(metricName)).asJava,
        snapshot.get98thPercentile * factor
      ),
      sampleBuilder.createSample(
        makeMetricName(metricName),
        "",
        (List("quantile") ++ makeLabelNames(metricName)).asJava,
        (List("0.99") ++ makeLabelValues(metricName)).asJava,
        snapshot.get99thPercentile * factor
      ),
      sampleBuilder.createSample(
        makeMetricName(metricName),
        "",
        (List("quantile") ++ makeLabelNames(metricName)).asJava,
        (List("0.999") ++ makeLabelValues(metricName)).asJava,
        snapshot.get999thPercentile * factor
      ),
      sampleBuilder.createSample(makeMetricName(metricName), "_count", makeLabelNames(metricName).asJava, makeLabelValues(metricName).asJava, count)
    )
    new Collector.MetricFamilySamples(samples.get(0).name, Type.SUMMARY, helpMessage, samples)
  }

  private[kpr] def fromHistogram(metricName: MetricName, histogram: Histogram) =
    fromSnapshotAndCount(metricName, histogram.getSnapshot, histogram.count(), 1.0, getHelpMessage(metricName.getName, histogram))

  private[kpr] def fromTimer(metricName: MetricName, timer: Timer) =
    fromSnapshotAndCount(metricName, timer.getSnapshot, timer.count(), 1.0d / TimeUnit.SECONDS.toNanos(1L), getHelpMessage(metricName.getName, timer))

  private[kpr] def fromMeter(metricName: MetricName, meter: Meter) = {
    val sample = sampleBuilder.createSample(
      makeMetricName(metricName),
      "_total",
      makeLabelNames(metricName).asJava,
      makeLabelValues(metricName).asJava,
      meter.count()
    )
    new Collector.MetricFamilySamples(sample.name, Type.COUNTER, getHelpMessage(metricName.getName, meter), util.Arrays.asList(sample))
  }

  override def collect: util.List[Collector.MetricFamilySamples] = {
    val mfSamplesMap = new util.HashMap[String, Collector.MetricFamilySamples]
    registry.allMetrics().asScala.foreach {
      case x if x._2.isInstanceOf[Gauge[_]]  => addToMap(mfSamplesMap, fromGauge(x._1, x._2.asInstanceOf[Gauge[_]]))
      case x if x._2.isInstanceOf[Counter]   => addToMap(mfSamplesMap, fromCounter(x._1, x._2.asInstanceOf[Counter]))
      case x if x._2.isInstanceOf[Histogram] => addToMap(mfSamplesMap, fromHistogram(x._1, x._2.asInstanceOf[Histogram]))
      case x if x._2.isInstanceOf[Timer]     => addToMap(mfSamplesMap, fromTimer(x._1, x._2.asInstanceOf[Timer]))
      case x if x._2.isInstanceOf[Meter]     => addToMap(mfSamplesMap, fromMeter(x._1, x._2.asInstanceOf[Meter]))
    }

    new util.ArrayList[Collector.MetricFamilySamples](mfSamplesMap.values)
  }

  private def addToMap(mfSamplesMap: util.Map[String, Collector.MetricFamilySamples], newMfSamples: Collector.MetricFamilySamples): Unit =
    if (newMfSamples != null) {
      val currentMfSamples = mfSamplesMap.get(newMfSamples.name)
      if (currentMfSamples == null) mfSamplesMap.put(newMfSamples.name, newMfSamples)
      else {
        val samples = new util.ArrayList[MetricFamilySamples.Sample](currentMfSamples.samples)
        samples.addAll(newMfSamples.samples)
        mfSamplesMap.put(
          newMfSamples.name,
          new Collector.MetricFamilySamples(newMfSamples.name, currentMfSamples.`type`, currentMfSamples.help, samples)
        )
      }
    }

  override def describe = new util.ArrayList[Collector.MetricFamilySamples]

  private def getHelpMessage(metricName: String, metric: Metric) =
    String.format("Generated from Yammer metric import (metric=%s, type=%s)", metricName, metric.getClass.getName)

  private def makeMetricName(metricName: MetricName): String =
    Collector.sanitizeMetricName(metricName.getGroup + "_" + metricName.getName + "_" + metricName.getType)

  private def makeLabelNames(metricName: MetricName): List[String] =
    if (metricName.hasScope) {
      val all = metricName.getScope.split("\\.").toList
      all.indices.collect { case i if i % 2 == 0 => all(i) }.toList
    } else {
      List()
    }

  private def makeLabelValues(metricName: MetricName): List[String] =
    if (metricName.hasScope) {
      val all = metricName.getScope.split("\\.").toList
      all.indices.collect { case i if i % 2 == 1 => all(i) }.toList
    } else {
      List()
    }
}
