package com.kng0501.dbqueue.persistence;

import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.JobStatus;
import java.time.Instant;
import java.util.UUID;

public interface JobProjection {

    long getJobId();

    long getMonsterId();

    String getPrompt();

    JobStatus getStatus();

    int getAttemptCount();

    Instant getNextAttemptAt();

    Instant getDeadlineAt();

    UUID getClaimToken();

    Instant getCreatedAt();

    Instant getStartedAt();

    Instant getFinishedAt();

    String getLastError();

    default Job toJob() {
        return new Job(
                getJobId(), getMonsterId(), getPrompt(), getStatus(), getAttemptCount(),
                getNextAttemptAt(), getDeadlineAt(), getClaimToken(), getCreatedAt(),
                getStartedAt(), getFinishedAt(), getLastError()
        );
    }
}
