package com.kng0501.dbqueue.worker.domain;

import java.time.Instant;
import java.util.UUID;

public record Job(
        long jobId,
        long monsterId,
        String prompt,
        JobStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant deadlineAt,
        UUID claimToken,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String lastError
) {
}
