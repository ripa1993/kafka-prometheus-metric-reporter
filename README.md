# kafka-prometheus-metric-reporter

Build with `sbt assembly`

Copy `target/scala-2.12/kafka-prometheus-reporter-assembly-0.1.jar` to Kafka classpath 

Add `metrics.reporter=dev.ripaz.kpr.PrometheusReporter` in `server.properties`

Open `{BROKER}:8080/metrics`
