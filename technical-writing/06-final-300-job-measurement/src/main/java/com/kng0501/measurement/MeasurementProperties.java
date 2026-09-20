package com.kng0501.measurement;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "measurement")
public final class MeasurementProperties {

    private String baseUrl = "http://127.0.0.1:8080";
    private int jobCount = 300;
    private int requestConcurrency = 35;
    private Duration timeout = Duration.ofMinutes(10);
    private Duration pollingInterval = Duration.ofSeconds(1);
    private Path outputDirectory = Path.of("06-final-300-job-measurement", "results");
    private int expectedWorkerConcurrency = 8;
    private Duration expectedGeneratorDelay = Duration.ofSeconds(5);

    public void validate() {
        final URI uri = parseBaseUrl();
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("measurement.base-url은 http 또는 https URL이어야 합니다.");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("measurement.base-url에는 호스트만 사용하고 인증정보를 포함할 수 없습니다.");
        }
        if (jobCount < 1 || requestConcurrency < 1 || requestConcurrency > jobCount) {
            throw new IllegalArgumentException(
                    "measurement.job-count >= 1, request-concurrency >= 1, request-concurrency <= job-count이어야 합니다."
            );
        }
        requirePositive(timeout, "measurement.timeout");
        requirePositive(pollingInterval, "measurement.polling-interval");
        if (expectedWorkerConcurrency < 1) {
            throw new IllegalArgumentException("measurement.expected-worker-concurrency는 1 이상이어야 합니다.");
        }
        if (expectedGeneratorDelay == null || expectedGeneratorDelay.isNegative()) {
            throw new IllegalArgumentException("measurement.expected-generator-delay는 0 이상이어야 합니다.");
        }
        if (outputDirectory == null) {
            throw new IllegalArgumentException("measurement.output-directory는 비어 있을 수 없습니다.");
        }
    }

    public URI baseUri() {
        return parseBaseUrl();
    }

    private URI parseBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("measurement.base-url은 비어 있을 수 없습니다.");
        }
        try {
            return URI.create(baseUrl);
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException("measurement.base-url은 유효한 URL이어야 합니다.", exception);
        }
    }

    private static void requirePositive(final Duration value, final String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + "은 0보다 커야 합니다.");
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getJobCount() {
        return jobCount;
    }

    public void setJobCount(final int jobCount) {
        this.jobCount = jobCount;
    }

    public int getRequestConcurrency() {
        return requestConcurrency;
    }

    public void setRequestConcurrency(final int requestConcurrency) {
        this.requestConcurrency = requestConcurrency;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(final Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getPollingInterval() {
        return pollingInterval;
    }

    public void setPollingInterval(final Duration pollingInterval) {
        this.pollingInterval = pollingInterval;
    }

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(final Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public int getExpectedWorkerConcurrency() {
        return expectedWorkerConcurrency;
    }

    public void setExpectedWorkerConcurrency(final int expectedWorkerConcurrency) {
        this.expectedWorkerConcurrency = expectedWorkerConcurrency;
    }

    public Duration getExpectedGeneratorDelay() {
        return expectedGeneratorDelay;
    }

    public void setExpectedGeneratorDelay(final Duration expectedGeneratorDelay) {
        this.expectedGeneratorDelay = expectedGeneratorDelay;
    }
}
