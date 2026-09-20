package com.kng0501.measurement;

import java.time.Instant;

public record MeasurementSummary(
        String runId,
        Instant startedAt,
        Instant finishedAt,
        boolean allAcceptedJobsTerminal,
        Long totalCompletionMillis,
        long observedDurationMillis,
        int requestCount,
        int http202Count,
        int httpFailureCount,
        int uniqueJobIdCount,
        int duplicateJobIdCount,
        int succeededCount,
        int failedCount,
        int pendingCount,
        int runningCount,
        int unfinishedCount,
        int missingAcceptedJobCount,
        int succeededWithoutImageCount,
        int misconnectedResultCount,
        int retriedJobCount,
        long additionalAttemptCount,
        int maximumPendingCount,
        LatencyPercentiles requestLatency,
        LatencyPercentiles jobWaitingTime,
        LatencyPercentiles jobProcessingTime
) {
}
