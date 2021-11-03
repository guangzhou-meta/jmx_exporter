package io.prometheus.jmx;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Smoke test for the metricFilter configuration.
 * <p/>
 * Run with
 * <pre>mvn verify</pre>
 */
@RunWith(Parameterized.class)
public class MetricFilterIT {

    private final TestSetup testSetup;

    @Parameterized.Parameters(name = "{0}")
    public static String[] dist() {
        return new String[]{"agent_java6", "agent", "httpserver" };
    }

    public MetricFilterIT(String distribution) throws IOException, URISyntaxException {
        switch (distribution) {
            case "agent_java6":
                testSetup = new AgentTestSetup("openjdk:11-jre", "jmx_prometheus_javaagent_java6", "config-agent");
                break;
            case "agent":
                testSetup = new AgentTestSetup("openjdk:11-jre", "jmx_prometheus_javaagent", "config-agent");
                break;
            case "httpserver":
                testSetup = new HttpServerTestSetup("openjdk:11-jre", "config-httpserver");
                break;
            default:
                throw new IllegalStateException(distribution + ": unknown distribution");
        }
    }

    @Test
    public void testMetricFilter() throws Exception {
        String deadlocked = "jvm_threads_deadlocked ";
        String deadlockedMonitor = "jvm_threads_deadlocked_monitor ";
        List<String> metrics = testSetup.scrape(SECONDS.toMillis(10));
        Optional<String> metric;

        // config.yml -> all metrics should exist
        metric = metrics.stream().filter(line -> line.startsWith(deadlocked)).findAny();
        Assert.assertTrue(deadlocked + "should exist", metric.isPresent());
        metric = metrics.stream().filter(line -> line.startsWith(deadlockedMonitor)).findAny();
        Assert.assertTrue(deadlockedMonitor + "should exist", metric.isPresent());

        // config-metric-filter-1.yml -> jvm_threads_deadlocked should be filtered
        testSetup.copyConfig("-metric-filter-1.yml");
        metrics = testSetup.scrape(SECONDS.toMillis(10));
        metric = metrics.stream().filter(line -> line.startsWith(deadlocked)).findAny();
        Assert.assertFalse(deadlocked + "should be filtered out", metric.isPresent());
        metric = metrics.stream().filter(line -> line.startsWith(deadlockedMonitor)).findAny();
        Assert.assertTrue(deadlockedMonitor + "should exist", metric.isPresent());

        // config-metric-filter-2.yml -> all metrics starting with jvm_threads_deadlocked should be filtered
        testSetup.copyConfig("-metric-filter-2.yml");
        metrics = testSetup.scrape(SECONDS.toMillis(10));
        metric = metrics.stream().filter(line -> line.startsWith(deadlocked)).findAny();
        Assert.assertFalse(deadlocked + "should be filtered out", metric.isPresent());
        metric = metrics.stream().filter(line -> line.startsWith(deadlockedMonitor)).findAny();
        Assert.assertFalse(deadlockedMonitor + "should be filtered out", metric.isPresent());
    }
}
