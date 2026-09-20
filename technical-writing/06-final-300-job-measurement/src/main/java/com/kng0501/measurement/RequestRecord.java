package com.kng0501.measurement;

import java.time.Instant;

public record RequestRecord(
        int requestNumber,
        String prompt,
        int httpStatus,
        Long jobId,
        Long monsterId,
        Instant startedAt,
        Instant finishedAt,
        long latencyMillis,
        String failureReason
) {

    public boolean hasAcceptedJob() {
        return httpStatus == 202 && jobId != null && monsterId != null && failureReason == null;
    }
}
