package com.kng0501.dbqueue.application;

import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.JobStatus;
import com.kng0501.dbqueue.domain.QueueSettings;
import com.kng0501.dbqueue.persistence.ImageGenerationJobJpaRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpiredJobTransition {

    private static final String EXPIRED_REASON = "processing deadline expired";

    private final ImageGenerationJobJpaRepository jobs;
    private final QueueSettings settings;

    public ExpiredJobTransition(
            final ImageGenerationJobJpaRepository jobs,
            final QueueSettings settings
    ) {
        this.jobs = jobs;
        this.settings = settings;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recover(final Job expired, final Instant now) {
        if (expired.attemptCount() >= settings.maxAttempts()) {
            return jobs.finishExpired(
                    expired.jobId(), expired.claimToken(), expired.attemptCount(), now,
                    EXPIRED_REASON, JobStatus.RUNNING, JobStatus.FAILED
            ) == 1;
        }
        return jobs.retryExpired(
                expired.jobId(), expired.claimToken(), expired.attemptCount(), now,
                now.plus(settings.retryDelay()), EXPIRED_REASON,
                JobStatus.RUNNING, JobStatus.PENDING
        ) == 1;
    }
}
