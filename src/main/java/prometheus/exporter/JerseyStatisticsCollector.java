package prometheus.exporter;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.CounterMetricFamily;
import io.prometheus.client.GaugeMetricFamily;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.monitoring.ExceptionMapperStatistics;
import org.glassfish.jersey.server.monitoring.MonitoringStatistics;
import org.glassfish.jersey.server.monitoring.ResourceMethodStatistics;
import org.glassfish.jersey.server.monitoring.ResourceStatistics;
import org.glassfish.jersey.server.monitoring.ResponseStatistics;
import org.glassfish.jersey.server.monitoring.TimeWindowStatistics;

/**
 * Collect metrics from Jersey framework (inside GlassFish/Payara server).
 * <p>
 * Usage example:
 * <pre>
 * new JerseyStatisticsCollector(monitoringStatistics).register();
 * </pre>
 * Monitoring statistics instance can be injected into JavaEE application like this:
 * <pre>
 * @Inject
 * private MonitoringStatistics monitoringStatisticsProvider;
 * </pre>
 * In order for exporter to work, statistics collection has to be enabled in the ResourceConfig
 * subclass:
 * <pre>
 * properties.put(ServerProperties.MONITORING_STATISTICS_ENABLED, true);
 * </pre>
 * Analogously in web.xml:
 * <pre>
 * <init-param>
 *      <param-name>jersey.config.server.monitoring.statistics.enabled</param-name>
 *      <param-value>true</param-value>
 * </init-param>
 * </pre>
 *
 * @author Viktoriia Bakalova
 * </p>
 */
public class JerseyStatisticsCollector extends Collector {

  private final MonitoringStatistics monitoringStatistics;

  /**
   * Creates a new collector for the given monitoring statistics provider.
   *
   * @param monitoringStatistics The Jersey monitoring statistics provider to collect
   * metrics for
   */
  public JerseyStatisticsCollector(MonitoringStatistics monitoringStatistics) {
    if (monitoringStatistics == null) {
      throw new IllegalArgumentException("Monitoring statistics cannot be null");
    }
    this.monitoringStatistics = monitoringStatistics;
  }

  @Override
  public List<Collector.MetricFamilySamples> collect() {
    List<Collector.MetricFamilySamples> metrics = new ArrayList<>();
    metrics.addAll(getExceptionMapperStatistics());
    metrics.addAll(getResponseStatistics());
    metrics.addAll(getUriStatistics());
    return metrics;
  }

  @Override
  public <T extends Collector> T register(CollectorRegistry registry) {
    return super.register(registry);
  }

  private List<MetricFamilySamples> getExceptionMapperStatistics() {
    ExceptionMapperStatistics exceptionMapperStats = this.monitoringStatistics
        .getExceptionMapperStatistics();
    List<MetricFamilySamples> metrics = new ArrayList<>();

    CounterMetricFamily successfulMappingsCount = new CounterMetricFamily(
        "jersey_exception_mappings_count_successful",
        "Count of all successful exception mappings",
        Collections.EMPTY_LIST
    );
    successfulMappingsCount
        .addMetric(Collections.EMPTY_LIST, exceptionMapperStats.getSuccessfulMappings());
    metrics.add(successfulMappingsCount);

    CounterMetricFamily unsuccessfulMappingsCount = new CounterMetricFamily(
        "jersey_exception_mappings_count_unsuccessful",
        "Count of all unsuccessful exception mappings",
        Collections.EMPTY_LIST
    );
    unsuccessfulMappingsCount
        .addMetric(Collections.EMPTY_LIST, exceptionMapperStats.getUnsuccessfulMappings());
    metrics.add(unsuccessfulMappingsCount);

    CounterMetricFamily totalMappingsCount = new CounterMetricFamily(
        "jersey_exception_mappings_count_total",
        "Total count of exception mappings",
        Collections.EMPTY_LIST
    );
    totalMappingsCount.addMetric(Collections.EMPTY_LIST, exceptionMapperStats.getTotalMappings());
    metrics.add(totalMappingsCount);

    List<String> labelNames = Arrays.asList("class_name");
    Map<Class<?>, Long> exceptionMapperExecutions = exceptionMapperStats
        .getExceptionMapperExecutions();
    for (Map.Entry<Class<?>, Long> entry : exceptionMapperExecutions.entrySet()) {
      CounterMetricFamily exceptionMapperExecutionCount = new CounterMetricFamily(
          "jersey_exception_mapper_execution_count",
          "Total count of exception mapper executions",
          labelNames
      );
      exceptionMapperExecutionCount
          .addMetric(Collections.singletonList(entry.getKey().getCanonicalName()),
              entry.getValue());
      metrics.add(exceptionMapperExecutionCount);
    }

    return metrics;
  }

  private List<MetricFamilySamples> getResponseStatistics() {
    ResponseStatistics responseStatistics = this.monitoringStatistics.getResponseStatistics();
    Map<Integer, Long> responseCodes = responseStatistics.getResponseCodes();
    List<String> labelNames = Arrays.asList("response_code");
    List<MetricFamilySamples> metrics = new ArrayList<>();
    for (Map.Entry<Integer, Long> responseCode : responseCodes.entrySet()) {
      CounterMetricFamily requestCount = new CounterMetricFamily(
          "jersey_response_count",
          "Count of responses with certain response code",
          labelNames
      );
      requestCount.addMetric(
          Collections.singletonList(responseCode.getKey().toString()),
          responseCode.getValue()
      );
      metrics.add(requestCount);
    }
    return metrics;
  }

  private List<MetricFamilySamples> getUriStatistics() {
    Map<String, ResourceStatistics> uriStatistics = this.monitoringStatistics.getUriStatistics();
    List<String> labelNames = Arrays.asList("uri", "method", "interval");
    List<String> labelValues = new ArrayList<>();
    List<MetricFamilySamples> metrics = new ArrayList<>();
    for (Map.Entry<String, ResourceStatistics> uriStatsEntry : uriStatistics.entrySet()) {
      labelValues.add(uriStatsEntry.getKey());
      Map<ResourceMethod, ResourceMethodStatistics> methodStatistics = uriStatsEntry.getValue()
          .getResourceMethodStatistics();
      for (Map.Entry<ResourceMethod, ResourceMethodStatistics> methodStatisticsEntry : methodStatistics
          .entrySet()) {
        labelValues.add(methodStatisticsEntry.getKey().getHttpMethod());
        Map<Long, TimeWindowStatistics> methodStatsIntervals = methodStatisticsEntry.getValue()
            .getMethodStatistics().getTimeWindowStatistics();
        for (Map.Entry<Long, TimeWindowStatistics> methodStatsIntervalEntry : methodStatsIntervals
            .entrySet()) {
          GaugeMetricFamily minRequestDuration = new GaugeMetricFamily(
              "jersey_request_duration_min_seconds",
              "Minimum request duration within interval",
              labelNames);
          minRequestDuration.addMetric(
              Arrays.asList(uriStatsEntry.getKey(), methodStatisticsEntry.getKey().getHttpMethod(),
                  Long.valueOf(methodStatsIntervalEntry.getKey() / 1000).toString()),
              methodStatsIntervalEntry.getValue().getMinimumDuration() / 1000.0
          );
          metrics.add(minRequestDuration);

          GaugeMetricFamily maxRequestDuration = new GaugeMetricFamily(
              "jersey_request_duration_max_seconds",
              "Maximum request duration within interval",
              labelNames);
          maxRequestDuration.addMetric(
              Arrays.asList(uriStatsEntry.getKey(), methodStatisticsEntry.getKey().getHttpMethod(),
                  Long.valueOf(methodStatsIntervalEntry.getKey() / 1000).toString()),
              methodStatsIntervalEntry.getValue().getMaximumDuration() / 1000.0
          );
          metrics.add(maxRequestDuration);

          GaugeMetricFamily avgRequestDuration = new GaugeMetricFamily(
              "jersey_request_duration_avg_seconds",
              "Average request duration within interval",
              labelNames);
          avgRequestDuration.addMetric(
              Arrays.asList(uriStatsEntry.getKey(), methodStatisticsEntry.getKey().getHttpMethod(),
                  Long.valueOf(methodStatsIntervalEntry.getKey() / 1000).toString()),
              methodStatsIntervalEntry.getValue().getAverageDuration() / 1000.0
          );
          metrics.add(avgRequestDuration);

          CounterMetricFamily requestCount = new CounterMetricFamily(
              "jersey_request_count",
              "Request count within interval",
              labelNames
          );
          requestCount.addMetric(
              Arrays.asList(uriStatsEntry.getKey(), methodStatisticsEntry.getKey().getHttpMethod(),
                  Long.valueOf(methodStatsIntervalEntry.getKey() / 1000).toString()),
              methodStatsIntervalEntry.getValue().getRequestCount()
          );
          metrics.add(requestCount);

          GaugeMetricFamily requestsPerSecond = new GaugeMetricFamily(
              "jersey_requests_per_second_total",
              "Number of requests per second within interval",
              labelNames);
          requestsPerSecond.addMetric(
              Arrays.asList(uriStatsEntry.getKey(), methodStatisticsEntry.getKey().getHttpMethod(),
                  Long.valueOf(methodStatsIntervalEntry.getKey() / 1000).toString()),
              methodStatsIntervalEntry.getValue().getRequestsPerSecond()
          );
          metrics.add(requestsPerSecond);
        }
      }
    }

    return metrics;
  }

}
