package com.github.ripa1993.kafkaprometheusreporter

import java.util

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.github.ripa1993.kafkaprometheusreporter.yammer.YammerExports
import com.typesafe.scalalogging.LazyLogging
import com.yammer.metrics.Metrics
import io.prometheus.client.hotspot.DefaultExports
import io.prometheus.client.{CollectorRegistry, Gauge}
import org.apache.kafka.common.metrics.{KafkaMetric, MetricsReporter}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class PrometheusReporter extends MetricsReporter with LazyLogging {

  implicit val system: ActorSystem = ActorSystem("prometheus-reporter")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  var prometheusGauges: Map[String, Gauge] = Map()
  var kafkaMetrics: mutable.Map[String, KafkaMetric] = mutable.Map()

  var reporterPort: Int = 8080
  var reporterInterface: String = "0.0.0.0"

  private val routes: Route = path("metrics") {
    get {
      complete(refreshAndServe)
    }
  }

  private def serverOnComplete(binding: Try[Http.ServerBinding]) = binding match {
    case Success(bound) =>
      println(s"Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/")
    case Failure(e) =>
      Console.err.println(s"Server could not start!")
      e.printStackTrace()
      system.terminate()
  }

  override def init(kafkaMetrics: util.List[KafkaMetric]): Unit =
    this.synchronized {
      logger.info("Initializing Prometheus metric reporter.")

      // Initialize metrics
      DefaultExports.initialize()
      CollectorRegistry.defaultRegistry.register(new YammerExports(Metrics.defaultRegistry()))
      kafkaMetrics.asScala.foreach(metricChange)

      // Start server
      Http().bindAndHandle(routes, reporterInterface, reporterPort).onComplete(serverOnComplete)

      logger.info("Successfully initialized Prometheus metric reporter.")
    }

  override def metricChange(metric: KafkaMetric): Unit =
    this.synchronized {
      logger.debug("Changed: " + metric.metricName() + " value: " + metric.metricValue())

      // Drop non-numeric metrics
      if (metric.metricName().group() == "app-info") {
        return
      }

      if (!isMetricRegistered(metric)) {
        registerMetric(metric)
      }

      prometheusGauges(PrometheusUtils.formatMetricName(metric))
        .labels(PrometheusUtils.metricLabelsValue(metric): _*)
        .set(metric.metricValue().asInstanceOf[java.lang.Double])
    }

  override def metricRemoval(metric: KafkaMetric): Unit =
    this.synchronized {
      logger.debug("Deleted: " + metric.metricName())
      kafkaMetrics.remove(PrometheusUtils.formatMetricNameWithLabels(metric))
    }

  override def close(): Unit = {}

  override def configure(configs: util.Map[String, _]): Unit =
    this.synchronized {
      val conf = configs.asScala

      reporterPort = conf.getOrElse(Constants.PROMETHEUS_REPORTER_PORT, reporterPort).asInstanceOf[Int]
      reporterInterface = conf.getOrElse(Constants.PROMETHEUS_REPORTER_INTERFACE, reporterInterface).asInstanceOf[String]
    }

  private def isMetricRegistered(metric: KafkaMetric): Boolean =
    prometheusGauges.contains(PrometheusUtils.formatMetricName(metric)) && kafkaMetrics.contains(PrometheusUtils.formatMetricNameWithLabels(metric))

  private def registerMetric(metric: KafkaMetric): Unit = {
    kafkaMetrics(PrometheusUtils.formatMetricNameWithLabels(metric)) = metric
    if (!prometheusGauges.contains(PrometheusUtils.formatMetricName(metric))) {
      prometheusGauges ++= Map(PrometheusUtils.formatMetricName(metric) -> PrometheusUtils.registerGauge(metric))
    }
  }

  private def refreshMetricValue(metric: KafkaMetric): Unit =
    try {
      prometheusGauges(PrometheusUtils.formatMetricName(metric))
        .labels(PrometheusUtils.metricLabelsValue(metric): _*)
        .set(metric.metricValue().toString.toDouble)
    } catch {
      case e: Throwable => logger.error(s"Cannot update value ${metric.metricValue()} for ${metric.metricName()}", e)
    }

  private def refreshAndServe: PrometheusMetricsOutput =
    this.synchronized {
      logger.debug("Refreshing metrics...")
      kafkaMetrics.values.foreach(refreshMetricValue)
      logger.debug("Refreshed")
      PrometheusMetricsOutput(CollectorRegistry.defaultRegistry.metricFamilySamples())
    }

}
