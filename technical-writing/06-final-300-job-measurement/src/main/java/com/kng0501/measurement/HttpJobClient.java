package com.kng0501.measurement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class HttpJobClient {

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final MeasurementProperties properties;
    private final Clock clock;

    public HttpJobClient(
            final HttpClient client,
            final ObjectMapper objectMapper,
            final MeasurementProperties properties,
            final Clock clock
    ) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public RequestRecord register(final int requestNumber, final String prompt) {
        final Instant startedAt = clock.instant();
        final long startedNanos = System.nanoTime();
        int httpStatus = -1;
        try {
            final HttpRequest request = HttpRequest.newBuilder(jobEndpoint())
                    .timeout(properties.getTimeout())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(new CreateJobRequest(prompt))))
                    .build();
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            httpStatus = response.statusCode();
            final Instant finishedAt = clock.instant();
            final long latencyMillis = elapsedMillis(startedNanos);
            if (httpStatus != 202) {
                return failed(requestNumber, prompt, httpStatus, startedAt, finishedAt, latencyMillis, "HTTP " + httpStatus);
            }

            final JobAcceptedResponse accepted = objectMapper.readValue(response.body(), JobAcceptedResponse.class);
            if (accepted.jobId() < 1 || accepted.monsterId() < 1) {
                return failed(requestNumber, prompt, httpStatus, startedAt, finishedAt, latencyMillis, "invalid accepted response");
            }
            return new RequestRecord(
                    requestNumber, prompt, httpStatus, accepted.jobId(), accepted.monsterId(),
                    startedAt, finishedAt, latencyMillis, null
            );
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failed(requestNumber, prompt, httpStatus, startedAt, clock.instant(), elapsedMillis(startedNanos), "InterruptedException");
        } catch (final IOException | RuntimeException exception) {
            return failed(
                    requestNumber, prompt, httpStatus, startedAt, clock.instant(), elapsedMillis(startedNanos),
                    exception.getClass().getSimpleName()
            );
        }
    }

    public Optional<ObservedJob> findJob(final long jobId) {
        try {
            final HttpRequest request = HttpRequest.newBuilder(jobEndpoint(jobId))
                    .timeout(properties.getTimeout())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Job 상태 조회가 실패했습니다. HTTP status=" + response.statusCode());
            }
            return Optional.of(objectMapper.readValue(response.body(), ObservedJob.class));
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Job 상태 조회가 중단됐습니다.", exception);
        } catch (final IOException exception) {
            throw new IllegalStateException("Job 상태 조회 I/O가 실패했습니다.", exception);
        }
    }

    private RequestRecord failed(
            final int requestNumber,
            final String prompt,
            final int httpStatus,
            final Instant startedAt,
            final Instant finishedAt,
            final long latencyMillis,
            final String failureReason
    ) {
        return new RequestRecord(
                requestNumber, prompt, httpStatus, null, null,
                startedAt, finishedAt, latencyMillis, failureReason
        );
    }

    private URI jobEndpoint() {
        return endpoint("/jobs");
    }

    private URI jobEndpoint(final long jobId) {
        return endpoint("/jobs/" + jobId);
    }

    private URI endpoint(final String path) {
        final String baseUrl = properties.getBaseUrl();
        final String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalized + path);
    }

    private static long elapsedMillis(final long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private record CreateJobRequest(String prompt) {
    }

    private record JobAcceptedResponse(long jobId, long monsterId) {
    }
}
