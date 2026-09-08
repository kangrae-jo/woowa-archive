package com.kng0501.dbqueue.application;

import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.JobStatus;
import com.kng0501.dbqueue.persistence.jpa.ImageGenerationJobJpaRepository;
import com.kng0501.dbqueue.persistence.jpa.JobProjection;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExpiredJobRecovery {

    private static final int RECOVERY_BATCH_SIZE = 100;

    private final Clock clock;
    private final ImageGenerationJobJpaRepository jobs;
    private final ExpiredJobTransition transition;

    public ExpiredJobRecovery(
            final Clock clock,
            final ImageGenerationJobJpaRepository jobs,
            final ExpiredJobTransition transition
    ) {
        this.clock = clock;
        this.jobs = jobs;
        this.transition = transition;
    }

    public int recoverExpired() {
        final Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        final List<Job> expiredJobs = jobs.findExpired(
                        JobStatus.RUNNING, now, PageRequest.of(0, RECOVERY_BATCH_SIZE)
                ).stream()
                .map(JobProjection::toJob)
                .toList();

        int recovered = 0;
        for (final Job expired : expiredJobs) {
            try {
                if (transition.recover(expired, now)) {
                    recovered++;
                    log.warn("job_id={} attempt_count={} cause=processing deadline expired",
                            expired.jobId(), expired.attemptCount());
                }
            } catch (final RuntimeException failure) {
                log.error("job_id={} attempt_count={} cause=timeout recovery persistence failed",
                        expired.jobId(), expired.attemptCount(), failure);
            }
        }
        return recovered;
    }
}
