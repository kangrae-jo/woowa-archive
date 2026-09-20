package com.kng0501.measurement;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MeasurementResultWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void 결과_파일은_필요한_형식만_기록하고_민감정보를_포함하지_않는다() throws Exception {
        final MeasurementResultWriter writer = new MeasurementResultWriter(new ObjectMapper().findAndRegisterModules());
        final Instant now = Instant.parse("2026-09-15T00:00:00Z");
        final MeasurementSummary summary = new MeasurementSummary(
                "run-1", now, now.plusSeconds(1), true, 1_000L, 1_000L,
                1, 1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, new LatencyPercentiles(1L, 1L, 1L), new LatencyPercentiles(1L, 1L, 1L),
                new LatencyPercentiles(1L, 1L, 1L)
        );
        final EnvironmentReport environment = new EnvironmentReport(
                "run-1", now, "http://127.0.0.1:8080", "technical_writing_measurement",
                300, 35, Duration.ofMinutes(10), Duration.ofSeconds(1), 8, Duration.ofSeconds(5),
                "21.0.8", "Mac OS X", "15.6", "aarch64", "8.4.6"
        );
        final RequestRecord request = new RequestRecord(
                1, "measurement-run-1", 202, 1L, 2L, now, now.plusMillis(1), 1L, null
        );
        final ObservedJob job = new ObservedJob(
                1L, 2L, "measurement-run-1", JobStatus.SUCCEEDED, 1,
                now, null, now, now, now.plusSeconds(1), null, "image:measurement-run-1"
        );

        writer.write(temporaryDirectory, summary, environment, List.of(request), Map.of(1L, job));

        final Path resultDirectory = temporaryDirectory.resolve("run-1");
        final String environmentJson = Files.readString(resultDirectory.resolve("environment.json"));
        assertAll(
                () -> assertTrue(Files.exists(resultDirectory.resolve("summary.json"))),
                () -> assertTrue(Files.exists(resultDirectory.resolve("jobs.csv"))),
                () -> assertTrue(Files.exists(resultDirectory.resolve("request-latency.csv"))),
                () -> assertFalse(environmentJson.toLowerCase().contains("password")),
                () -> assertFalse(environmentJson.contains("jdbc:mysql")),
                () -> assertFalse(environmentJson.contains("TECHNICAL_WRITING_DB_URL")),
                () -> assertTrue(environmentJson.contains("21.0.8")),
                () -> assertTrue(environmentJson.contains("8.4.6"))
        );
    }
}
