package com.kng0501.measurement;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MeasurementSummaryCalculatorTest {

    private final MeasurementSummaryCalculator calculator = new MeasurementSummaryCalculator();

    @Test
    void 누락_미종결_재시도와_결과_오연결을_집계한다() {
        final Instant startedAt = Instant.parse("2026-09-15T00:00:00Z");
        final List<RequestRecord> requests = List.of(
                request(1, 10L, 20L, 202, 10),
                request(2, 11L, 21L, 202, 20),
                request(3, 12L, 22L, 202, 30),
                request(4, null, null, 500, 40)
        );
        final List<ObservedJob> jobs = List.of(
                job(10L, JobStatus.SUCCEEDED, 1, startedAt.plusSeconds(1), startedAt.plusSeconds(3), null),
                job(11L, JobStatus.RUNNING, 3, startedAt.plusSeconds(2), null, null)
        );

        final MeasurementSummary summary = calculator.calculate(
                "run", startedAt, startedAt.plusSeconds(10), requests, jobs, 2
        );

        assertAll(
                () -> assertEquals(4, summary.requestCount()),
                () -> assertEquals(3, summary.http202Count()),
                () -> assertEquals(1, summary.httpFailureCount()),
                () -> assertEquals(3, summary.uniqueJobIdCount()),
                () -> assertEquals(1, summary.succeededCount()),
                () -> assertEquals(1, summary.runningCount()),
                () -> assertEquals(1, summary.unfinishedCount()),
                () -> assertEquals(1, summary.missingAcceptedJobCount()),
                () -> assertEquals(1, summary.succeededWithoutImageCount()),
                () -> assertEquals(1, summary.misconnectedResultCount()),
                () -> assertEquals(1, summary.retriedJobCount()),
                () -> assertEquals(2L, summary.additionalAttemptCount()),
                () -> assertEquals(2, summary.maximumPendingCount()),
                () -> assertEquals(20L, summary.requestLatency().p50Millis()),
                () -> assertEquals(40L, summary.requestLatency().p95Millis()),
                () -> assertNull(summary.totalCompletionMillis()),
                () -> assertFalse(summary.allAcceptedJobsTerminal())
        );
    }

    @Test
    void 중복_jobId를_별도로_집계한다() {
        final Instant now = Instant.parse("2026-09-15T00:00:00Z");
        final List<RequestRecord> requests = List.of(
                request(1, 10L, 20L, 202, 10),
                request(2, 10L, 20L, 202, 10)
        );
        final List<ObservedJob> jobs = List.of(
                job(10L, JobStatus.SUCCEEDED, 1, now.plusSeconds(1), now.plusSeconds(2), "image:measurement-run-1")
        );

        final MeasurementSummary summary = calculator.calculate("run", now, now.plusSeconds(3), requests, jobs, 0);

        assertAll(
                () -> assertEquals(1, summary.uniqueJobIdCount()),
                () -> assertEquals(1, summary.duplicateJobIdCount()),
                () -> assertEquals(0, summary.missingAcceptedJobCount()),
                () -> assertEquals(2_000L, summary.totalCompletionMillis())
        );
    }

    @Test
    void jobId를_읽지_못한_HTTP_202은_접수_성공으로_처리하지_않는다() {
        final Instant now = Instant.parse("2026-09-15T00:00:00Z");
        final RequestRecord malformedAcceptedResponse = new RequestRecord(
                1, "measurement-run-1", 202, null, null, now, now.plusMillis(1), 1L, "invalid accepted response"
        );

        final MeasurementSummary summary = calculator.calculate(
                "run", now, now.plusSeconds(1), List.of(malformedAcceptedResponse), List.of(), 0
        );

        assertAll(
                () -> assertEquals(1, summary.http202Count()),
                () -> assertEquals(1, summary.httpFailureCount()),
                () -> assertEquals(0, summary.uniqueJobIdCount())
        );
    }

    private static RequestRecord request(
            final int requestNumber,
            final Long jobId,
            final Long monsterId,
            final int httpStatus,
            final long latencyMillis
    ) {
        final Instant now = Instant.parse("2026-09-15T00:00:00Z");
        return new RequestRecord(
                requestNumber,
                "measurement-run-" + requestNumber,
                httpStatus,
                jobId,
                monsterId,
                now,
                now.plusMillis(latencyMillis),
                latencyMillis,
                httpStatus == 202 ? null : "HTTP " + httpStatus
        );
    }

    private static ObservedJob job(
            final long jobId,
            final JobStatus status,
            final int attemptCount,
            final Instant startedAt,
            final Instant finishedAt,
            final String image
    ) {
        return new ObservedJob(
                jobId,
                jobId + 10,
                "measurement-run-" + (jobId - 9),
                status,
                attemptCount,
                startedAt,
                null,
                Instant.parse("2026-09-15T00:00:00Z"),
                startedAt,
                finishedAt,
                null,
                image
        );
    }
}
