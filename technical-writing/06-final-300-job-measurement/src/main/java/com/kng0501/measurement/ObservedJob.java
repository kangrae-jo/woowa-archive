package com.kng0501.measurement;

import java.time.Instant;

public record ObservedJob(
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

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public String expectedImage() {
        return "image:" + prompt;
    }

    public boolean hasMisconnectedImage() {
        return status == JobStatus.SUCCEEDED && !expectedImage().equals(image);
    }
}
