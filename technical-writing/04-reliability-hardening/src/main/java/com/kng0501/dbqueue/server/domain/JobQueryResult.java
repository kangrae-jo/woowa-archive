package com.kng0501.dbqueue.server.domain;

import java.time.Instant;

public record JobQueryResult(
        long jobId,
        long monsterId,
        String prompt,
        JobStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant deadlineAt,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String lastError,
        String image
) {
}
