### Jersey support for Prometheus Java Instrumentation library

This is Jersey exporter for Prometheus Java instrumentation library https://github.com/prometheus/client_java.

This exporter can be used to collect metrics from Jersey framework (inside GlassFish/Payara server).
 <p>
  Usage example:
  <pre>
  new JerseyStatisticsCollector(monitoringStatisticsProvider).register();
  </pre>
  Monitoring statistics provider can be injected into JavaEE application like this:
  <pre>
  @Inject
  private Provider<MonitoringStatistics> monitoringStatisticsProvider;
  </pre>
  In order for exporter to work, statistics collection has to be enabled in the ResourceConfig
  subclass:
  <pre>
  properties.put(ServerProperties.MONITORING_STATISTICS_ENABLED, true);
  </pre>
  Analogously in web.xml:
  <pre>
    <init-param>
         <param-name>jersey.config.server.monitoring.statistics.enabled</param-name>
         <param-value>true</param-value>
    </init-param>
  </pre>
 </p>
