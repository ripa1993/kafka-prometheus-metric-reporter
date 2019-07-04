package dev.ripaz.kpr

import java.io.{StringWriter, Writer}
import java.util

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model._
import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.exporter.common.TextFormat

case class PrometheusMetricsOutput(samples: util.Enumeration[MetricFamilySamples])

object PrometheusMetricsOutput {
  private val mediaTypeParams = Map("version" -> "0.0.4")
  private val mediaType = MediaType.customWithFixedCharset("text", "plain", HttpCharsets.`UTF-8`, params = mediaTypeParams)

  def toPrometheusTextFormat(e: PrometheusMetricsOutput): String = {
    val writer: Writer = new StringWriter()
    TextFormat.write004(writer, e.samples)

    writer.toString
  }

  implicit val metricsFamilySamplesMarshaller: ToEntityMarshaller[PrometheusMetricsOutput] = {
    Marshaller.withFixedContentType(mediaType) { s =>
      HttpEntity(mediaType, toPrometheusTextFormat(s))
    }
  }

}
