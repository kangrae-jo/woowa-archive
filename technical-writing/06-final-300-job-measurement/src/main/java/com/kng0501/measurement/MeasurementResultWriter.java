package com.kng0501.measurement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MeasurementResultWriter {

    private final ObjectMapper objectMapper;

    public MeasurementResultWriter(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            final Path outputDirectory,
            final MeasurementSummary summary,
            final EnvironmentReport environment,
            final List<RequestRecord> requests,
            final Map<Long, ObservedJob> jobsById
    ) throws IOException {
        final Path runDirectory = outputDirectory.resolve(summary.runId()).normalize();
        if (!runDirectory.startsWith(outputDirectory.normalize())) {
            throw new IllegalArgumentException("결과 디렉터리가 측정 출력 경로를 벗어납니다.");
        }
        Files.createDirectories(runDirectory);
        Files.writeString(runDirectory.resolve("summary.json"), prettyJson(summary), StandardCharsets.UTF_8);
        Files.writeString(runDirectory.resolve("environment.json"), environmentJson(environment), StandardCharsets.UTF_8);
        Files.writeString(runDirectory.resolve("jobs.csv"), jobsCsv(requests, jobsById), StandardCharsets.UTF_8);
        Files.writeString(runDirectory.resolve("request-latency.csv"), requestLatencyCsv(requests), StandardCharsets.UTF_8);
    }

    String environmentJson(final EnvironmentReport environment) throws JsonProcessingException {
        final String json = prettyJson(environment);
        final String lowerCase = json.toLowerCase(Locale.ROOT);
        if (lowerCase.contains("password") || lowerCase.contains("jdbc:") || lowerCase.contains("datasource")) {
            throw new IllegalArgumentException("환경 결과에는 인증정보나 DB URL을 포함할 수 없습니다.");
        }
        return json;
    }

    private String prettyJson(final Object value) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value) + System.lineSeparator();
    }

    private static String jobsCsv(final List<RequestRecord> requests, final Map<Long, ObservedJob> jobsById) {
        final List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
                "request_number", "prompt", "http_status", "job_id", "monster_id",
                "request_started_at", "request_finished_at", "request_latency_ms", "request_failure",
                "status", "attempt_count", "created_at", "started_at", "finished_at",
                "job_wait_ms", "processing_ms", "image", "expected_image", "result_misconnected", "missing_from_db"
        ));
        for (final RequestRecord request : requests) {
            final ObservedJob job = request.jobId() == null ? null : jobsById.get(request.jobId());
            rows.add(List.of(
                    Integer.toString(request.requestNumber()),
                    request.prompt(),
                    Integer.toString(request.httpStatus()),
                    valueOf(request.jobId()),
                    valueOf(request.monsterId()),
                    valueOf(request.startedAt()),
                    valueOf(request.finishedAt()),
                    Long.toString(request.latencyMillis()),
                    valueOf(request.failureReason()),
                    job == null ? "" : job.status().name(),
                    job == null ? "" : Integer.toString(job.attemptCount()),
                    job == null ? "" : valueOf(job.createdAt()),
                    job == null ? "" : valueOf(job.startedAt()),
                    job == null ? "" : valueOf(job.finishedAt()),
                    job == null ? "" : durationMillis(job.createdAt(), job.startedAt()),
                    job == null ? "" : durationMillis(job.startedAt(), job.finishedAt()),
                    job == null ? "" : valueOf(job.image()),
                    job == null ? "" : job.expectedImage(),
                    job != null && job.hasMisconnectedImage() ? "true" : "false",
                    request.hasAcceptedJob() && job == null ? "true" : "false"
            ));
        }
        return csv(rows);
    }

    private static String requestLatencyCsv(final List<RequestRecord> requests) {
        final List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
                "request_number", "prompt", "http_status", "job_id", "request_started_at",
                "request_finished_at", "request_latency_ms", "request_failure"
        ));
        for (final RequestRecord request : requests) {
            rows.add(List.of(
                    Integer.toString(request.requestNumber()),
                    request.prompt(),
                    Integer.toString(request.httpStatus()),
                    valueOf(request.jobId()),
                    valueOf(request.startedAt()),
                    valueOf(request.finishedAt()),
                    Long.toString(request.latencyMillis()),
                    valueOf(request.failureReason())
            ));
        }
        return csv(rows);
    }

    private static String durationMillis(final java.time.Instant startedAt, final java.time.Instant finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return "";
        }
        return Long.toString(Duration.between(startedAt, finishedAt).toMillis());
    }

    private static String valueOf(final Object value) {
        return value == null ? "" : value.toString();
    }

    private static String csv(final List<List<String>> rows) {
        return rows.stream()
                .map(MeasurementResultWriter::csvRow)
                .reduce("", (left, right) -> left + right + System.lineSeparator());
    }

    private static String csvRow(final List<String> values) {
        return values.stream()
                .map(MeasurementResultWriter::escapeCsv)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String escapeCsv(final String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
