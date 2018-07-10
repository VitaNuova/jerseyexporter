### Jersey support for Prometheus Java Instrumentation library

This is Jersey exporter for Prometheus Java instrumentation library https://github.com/prometheus/client_java.

This exporter can be used to collect metrics from Jersey framework (inside GlassFish/Payara server).
 <p>
  Usage example:
  <pre>
  new JerseyStatisticsCollector(monitoringStatistics).register();
  </pre>
  Monitoring statistics can be injected into JavaEE application like this:
  <pre>
  @Inject
  private MonitoringStatistics monitoringStatistics;
  </pre>
  In order for exporter to work, statistics collection has to be enabled in the ResourceConfig
  subclass:
  <pre>
  properties.put(ServerProperties.MONITORING_STATISTICS_ENABLED, true);
  </pre>
  Analogously in web.xml:
  <pre>
    &lt;init-param&gt;
         &lt;param-name&gt;jersey.config.server.monitoring.statistics.enabled&lt;/param-name&gt;
         &lt;param-value&gt;true&lt;/param-value&gt;
    &lt;/init-param&gt;
  </pre>
 </p>
