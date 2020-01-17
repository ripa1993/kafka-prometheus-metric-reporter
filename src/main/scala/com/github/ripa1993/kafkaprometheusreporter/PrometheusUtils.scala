package com.github.ripa1993.kafkaprometheusreporter

import io.prometheus.client.{Collector, Gauge}
import org.apache.kafka.common.metrics.KafkaMetric

import scala.collection.JavaConverters._

object PrometheusUtils {
  def registerGauge(metric: KafkaMetric): Gauge =
    Gauge
      .build()
      .name(formatMetricName(metric))
      .help(helpOrDefault(metric.metricName().description()))
      .labelNames(metricLabelsName(metric): _*)
      .register()

  def formatMetricName(metric: KafkaMetric): String = {
    val base = metric.metricName().group() + "_" + metric.metricName().name()

    if (metric.metricName().tags().size() > 0) {
      Collector.sanitizeMetricName(base + "_" + metricLabelsName(metric).mkString("_"))
    } else {
      Collector.sanitizeMetricName(base)
    }
  }

  def formatMetricNameWithLabels(metric: KafkaMetric): String = {
    val base = metric.metricName().group() + "_" + metric.metricName().name()

    if (metric.metricName().tags().size() > 0) {
      Collector.sanitizeMetricName(base + "_" + metricLabelsName(metric).mkString("_") + "_" + metricLabelsValue(metric).mkString("_"))
    } else {
      Collector.sanitizeMetricName(base)
    }
  }

  def metricLabelsName(metric: KafkaMetric): Array[String] =
    metric.metricName().tags().asScala.keys.map(x => x.replace("-", "_")).toArray

  def metricLabelsValue(metric: KafkaMetric): Array[String] =
    metric.metricName().tags().asScala.values.toArray

  def helpOrDefault(help: String): String =
    if (help.length == 0) {
      "N/A"
    } else {
      help
    }

}
