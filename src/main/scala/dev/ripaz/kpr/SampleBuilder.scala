package dev.ripaz.kpr

import java.util
import java.util.Collections

import io.prometheus.client.Collector
import io.prometheus.client.Collector.MetricFamilySamples


/**
  * SampleBuilder defines the action of creating a {@link io.prometheus.client.Collector.MetricFamilySamples.Sample} for the given parameters.
  */
trait SampleBuilder {
  /**
    * Creates a new {@link io.prometheus.client.Collector.MetricFamilySamples.Sample} for the given parameters.
    *
    * @param dropwizardName        Metric name coming from Dropwizard.
    * @param nameSuffix            Optional suffix to add.
    * @param additionalLabelNames  Optional additional label names. Needs to have same size as additionalLabelValues.
    * @param additionalLabelValues Optional additional label values. Needs to have same size as additionalLabelNames.
    * @param value                 Metric value
    * @return A new { @link io.prometheus.client.Collector.MetricFamilySamples.Sample}.
    */
  def createSample(dropwizardName: String, nameSuffix: String, additionalLabelNames: util.List[String], additionalLabelValues: util.List[String], value: Double): MetricFamilySamples.Sample
}

class DefaultSampleBuilder extends SampleBuilder {
  override def createSample(dropwizardName: String, nameSuffix: String, additionalLabelNames: util.List[String], additionalLabelValues: util.List[String], value: Double): MetricFamilySamples.Sample = {
    val suffix = if (nameSuffix == null) ""
    else nameSuffix
    val labelNames = if (additionalLabelNames == null) Collections.emptyList[String]
    else additionalLabelNames
    val labelValues = if (additionalLabelValues == null) Collections.emptyList[String]
    else additionalLabelValues
    new MetricFamilySamples.Sample(Collector.sanitizeMetricName(dropwizardName + suffix), new util.ArrayList[String](labelNames), new util.ArrayList[String](labelValues), value)
  }
}
