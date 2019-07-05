package dev.ripaz.kpr

import io.prometheus.client.Gauge
import org.apache.kafka.common.metrics.KafkaMetric

import scala.collection.JavaConverters._

object PrometheusUtils {
  def gaugeFromKafkaMetric(metric: KafkaMetric): Gauge = {
    Gauge.build()
      .name(metricNameString(metric))
      .help(helpOrDummy(metric.metricName().description()))
      .labelNames(metricLabelsName(metric):_*)
      .register()
  }

  def metricNameString(metric: KafkaMetric): String = {
    val base = metric.metricName().group() + "_" + metric.metricName().name()

    if (metric.metricName().tags().size() > 0){
      sanitize(base + "_" + metricLabelsName(metric).mkString("_"))
    } else {
      sanitize(base)
    }
  }

  def metricNameStringExtended(metric: KafkaMetric): String = {
    val base = metric.metricName().group() + "_" + metric.metricName().name()

    if (metric.metricName().tags().size() > 0){
      sanitize(base + "_" + metricLabelsName(metric).mkString("_") + "_" + metricLabelsValue(metric).mkString("_"))
    } else {
      sanitize(base)
    }
  }

  def metricLabelsValue(metric: KafkaMetric): Array[String] = {
    metric.metricName().tags().asScala.values.toArray
  }

  def metricLabelsName(metric: KafkaMetric): Array[String] = {
    metric.metricName().tags().asScala.keys.map(x => x.replace("-", "_")).toArray
  }

  def helpOrDummy(help: String): String = {
    if (help.length == 0){
      "N/A"
    } else {
      help
    }
  }

  def sanitize(input: String): String = {
    input.replace("-", "_")
  }

}
