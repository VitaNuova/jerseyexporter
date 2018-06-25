package prometheus.exporter;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.prometheus.client.CollectorRegistry;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Provider;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.monitoring.ExceptionMapperStatistics;
import org.glassfish.jersey.server.monitoring.MonitoringStatistics;
import org.glassfish.jersey.server.monitoring.ResourceMethodStatistics;
import org.glassfish.jersey.server.monitoring.ResourceStatistics;
import org.glassfish.jersey.server.monitoring.ResponseStatistics;
import org.glassfish.jersey.server.monitoring.TimeWindowStatistics;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.jvnet.hk2.internal.IterableProviderImpl;
import org.powermock.api.mockito.PowerMockito;

public class JerseyStatisticsCollectorTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private Provider<MonitoringStatistics> provider;
  private MonitoringStatistics monitoringStatistics;
  private CollectorRegistry collectorRegistry;
  private ExceptionMapperStatistics exceptionMapperStatistics;
  private ResponseStatistics responseStatistics;

  @Before
  public void before() {
    collectorRegistry = new CollectorRegistry();
    provider = mock(IterableProviderImpl.class);
    monitoringStatistics = mock(MonitoringStatistics.class);
    when(provider.get()).thenReturn(monitoringStatistics);

    exceptionMapperStatistics = mock(ExceptionMapperStatistics.class);
    when(monitoringStatistics.getExceptionMapperStatistics()).thenReturn(exceptionMapperStatistics);

    responseStatistics = mock(ResponseStatistics.class);
    when(monitoringStatistics.getResponseStatistics()).thenReturn(responseStatistics);
  }

  @Test
  public void shouldFailIfNoMonitoringStatisticsProviderPassed() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Monitoring statistics provider cannot be null");
    new JerseyStatisticsCollector(null);
  }

  @Test
  public void shouldFailIfNoMonitoringStatisticsAvailable() {
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("Monitoring statistics cannot be null");
    when(provider.get()).thenReturn(null);
    new JerseyStatisticsCollector(provider).register();
  }

  @Test
  public void shouldPublishExceptionMapperStatistics() {

    Map<Class<?>, Long> exceptionMapperExecutionCounts = new HashMap<>();
    exceptionMapperExecutionCounts.put(IllegalStateException.class, 4L);
    exceptionMapperExecutionCounts.put(IllegalArgumentException.class, 5L);

    when(exceptionMapperStatistics.getSuccessfulMappings()).thenReturn(1L);
    when(exceptionMapperStatistics.getUnsuccessfulMappings()).thenReturn(2L);
    when(exceptionMapperStatistics.getTotalMappings()).thenReturn(3L);
    when(exceptionMapperStatistics.getExceptionMapperExecutions()).thenReturn(exceptionMapperExecutionCounts);

    new JerseyStatisticsCollector(provider).register(collectorRegistry);

    assertThat(collectorRegistry.getSampleValue("jersey_exception_mappings_count_successful", new String[]{}, new String[]{}), is(1.0));
    assertThat(collectorRegistry.getSampleValue("jersey_exception_mappings_count_unsuccessful", new String[]{}, new String[]{}), is(2.0));
    assertThat(collectorRegistry.getSampleValue("jersey_exception_mappings_count_total", new String[]{}, new String[]{}), is(3.0));
    assertThat(collectorRegistry.getSampleValue( "jersey_exception_mapper_execution_count", new String[]{"class_name"}, new String[]{"java.lang.IllegalStateException"}), is(4.0));
    assertThat(collectorRegistry.getSampleValue( "jersey_exception_mapper_execution_count", new String[]{"class_name"}, new String[]{"java.lang.IllegalArgumentException"}), is(5.0));

  }

  @Test
  public void shouldPublishResponseStatistics() {
    Map<Integer, Long> responseCodes = new HashMap<>();
    responseCodes.put(200, 1L);
    responseCodes.put(500, 2L);
    when(responseStatistics.getResponseCodes()).thenReturn(responseCodes);

    new JerseyStatisticsCollector(provider).register(collectorRegistry);

    assertThat(collectorRegistry.getSampleValue("jersey_response_count", new String[]{"response_code"}, new String[]{"200"}), is(1.0));
    assertThat(collectorRegistry.getSampleValue("jersey_response_count", new String[]{"response_code"}, new String[]{"500"}), is(2.0));
  }

  @Test
  public void shouldPublishUriStatistics() {
    Map<Long, TimeWindowStatistics> timeWindowStatistics = new HashMap<>();
    timeWindowStatistics.put(10L, PowerMockito.mock(TimeWindowStatistics.class));
    timeWindowStatistics.put(100L, PowerMockito.mock(TimeWindowStatistics.class));
    for(Map.Entry<Long, TimeWindowStatistics> timeWindowStatisticsEntry: timeWindowStatistics.entrySet()) {
      when(timeWindowStatisticsEntry.getValue().getAverageDuration()).thenReturn(1L);
      when(timeWindowStatisticsEntry.getValue().getMaximumDuration()).thenReturn(2L);
      when(timeWindowStatisticsEntry.getValue().getMinimumDuration()).thenReturn(3L);
      when(timeWindowStatisticsEntry.getValue().getRequestCount()).thenReturn(4L);
      when(timeWindowStatisticsEntry.getValue().getRequestsPerSecond()).thenReturn(5.0);
    }

    Map<ResourceMethod, ResourceMethodStatistics> methodStatistics = new HashMap<>();

    ResourceMethod getMethod = mock(ResourceMethod.class);
    ResourceMethod postMethod = mock(ResourceMethod.class);
    when(getMethod.getHttpMethod()).thenReturn("GET");
    when(postMethod.getHttpMethod()).thenReturn("POST");
    ResourceMethodStatistics resourceMethodStatistics = mock(ResourceMethodStatistics.class);
    when(resourceMethodStatistics.getMethodStatistics().getTimeWindowStatistics()).thenReturn(timeWindowStatistics);
    methodStatistics.put(getMethod, resourceMethodStatistics);
    methodStatistics.put(postMethod, resourceMethodStatistics);

    Map<String, ResourceStatistics> uriStatistics = new HashMap<>();
    when(monitoringStatistics.getUriStatistics()).thenReturn(uriStatistics);

    ResourceStatistics resourceStatistics = mock(ResourceStatistics.class);
    when(resourceStatistics.getResourceMethodStatistics()).thenReturn(methodStatistics);
    uriStatistics.put("/", resourceStatistics);
    uriStatistics.put("/path", resourceStatistics);

    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_min_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/", "GET", "10"}), is(3.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_min_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/", "GET", "100"}), is(3.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_min_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/", "POST", "10"}), is(3.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_min_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/", "POST", "100"}), is(3.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_min_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/path", "GET", "10"}), is(3.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_min_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/path", "GET", "100"}), is(3.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_min_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/path", "POST", "10"}), is(3.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_min_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/path", "POST", "100"}), is(3.0));

    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_max_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/", "GET", "10"}), is(2.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_max_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/", "GET", "100"}), is(2.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_max_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/", "POST", "10"}), is(2.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_max_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/", "POST", "100"}), is(2.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_max_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/path", "GET", "10"}), is(2.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_max_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/path", "GET", "100"}), is(2.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_max_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/path", "POST", "10"}), is(2.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_max_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/path", "POST", "100"}), is(2.0));

    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_avg_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/", "GET", "10"}), is(1.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_avg_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/", "GET", "100"}), is(1.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_avg_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/", "POST", "10"}), is(1.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_avg_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/", "POST", "100"}), is(1.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_avg_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/path", "GET", "10"}), is(1.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_avg_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/path", "GET", "100"}), is(1.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_avg_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/path", "POST", "10"}), is(1.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_duration_avg_seconds", new String[]{"uri", "method", "interval"}, new String[]{"/path", "POST", "100"}), is(1.0));

    assertThat(collectorRegistry.getSampleValue("jersey_request_count", new String[]{"uri", "method", "interval"}, new String[]{"/", "GET", "10"}), is(4.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_count", new String[]{"uri", "method", "interval"}, new String[]{"/", "GET", "100"}), is(4.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_count", new String[]{"uri", "method", "interval"}, new String[]{"/", "POST", "10"}), is(4.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_count", new String[]{"uri", "method", "interval"}, new String[]{"/", "POST", "100"}), is(4.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_count", new String[]{"uri", "method", "interval"}, new String[]{"/path", "GET", "10"}), is(4.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_count", new String[]{"uri", "method", "interval"}, new String[]{"/path", "GET", "100"}), is(4.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_count", new String[]{"uri", "method", "interval"}, new String[]{"/path", "POST", "10"}), is(4.0));
    assertThat(collectorRegistry.getSampleValue("jersey_request_count", new String[]{"uri", "method", "interval"}, new String[]{"/path", "POST", "100"}), is(4.0));

    assertThat(collectorRegistry.getSampleValue("jersey_requests_per_second_total", new String[]{"uri", "method", "interval"}, new String[]{"/", "GET", "10"}), is(5.0));
    assertThat(collectorRegistry.getSampleValue("jersey_requests_per_second_total", new String[]{"uri", "method", "interval"}, new String[]{"/", "GET", "100"}), is(5.0));
    assertThat(collectorRegistry.getSampleValue("jersey_requests_per_second_total", new String[]{"uri", "method", "interval"}, new String[]{"/", "POST", "10"}), is(5.0));
    assertThat(collectorRegistry.getSampleValue("jersey_requests_per_second_total", new String[]{"uri", "method", "interval"}, new String[]{"/", "POST", "100"}), is(5.0));
    assertThat(collectorRegistry.getSampleValue("jersey_requests_per_second_total", new String[]{"uri", "method", "interval"}, new String[]{"/path", "GET", "10"}), is(5.0));
    assertThat(collectorRegistry.getSampleValue("jersey_requests_per_second_total", new String[]{"uri", "method", "interval"}, new String[]{"/path", "GET", "100"}), is(5.0));
    assertThat(collectorRegistry.getSampleValue("jersey_requests_per_second_total", new String[]{"uri", "method", "interval"}, new String[]{"/path", "POST", "10"}), is(5.0));
    assertThat(collectorRegistry.getSampleValue("jersey_requests_per_second_total", new String[]{"uri", "method", "interval"}, new String[]{"/path", "POST", "100"}), is(5.0));

  }
}
