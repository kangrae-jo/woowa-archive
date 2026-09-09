package com.kng0501.dbqueue.worker.application;

import com.kng0501.dbqueue.worker.domain.Job;
import com.kng0501.dbqueue.worker.domain.JobStatus;
import com.kng0501.dbqueue.worker.domain.QueueSettings;
import com.kng0501.dbqueue.worker.persistence.ImageResultUpdater;
import com.kng0501.dbqueue.worker.persistence.jpa.ImageGenerationJobJpaRepository;
import com.kng0501.dbqueue.worker.persistence.jpa.JobProjection;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobQueue {

    private final Clock clock;
    private final QueueSettings settings;
    private final ImageGenerationJobJpaRepository jobs;
    private final ImageResultUpdater results;

    public JobQueue(
            final Clock clock,
            final QueueSettings settings,
            final ImageGenerationJobJpaRepository jobs,
            final ImageResultUpdater results
    ) {
        this.clock = clock;
        this.settings = settings;
        this.jobs = jobs;
        this.results = results;
    }

    @Transactional(readOnly = true)
    public Optional<Long> findCandidate() {
        return jobs.findCandidates(JobStatus.PENDING, now(), settings.maxAttempts(), PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Job> tryClaim(final long jobId) {
        final Instant now = now();
        final UUID token = UUID.randomUUID();
        final int changed = jobs.claim(
                jobId,
                token,
                now,
                now.plus(settings.processingTimeout()),
                settings.maxAttempts(),
                JobStatus.PENDING,
                JobStatus.RUNNING
        );
        if (changed == 0) {
            return Optional.empty();
        }
        return jobs.findSnapshotById(jobId).map(JobProjection::toJob);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(final long jobId, final UUID token, final String image) {
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("생성 이미지는 비어 있을 수 없습니다.");
        }
        final Instant now = now();
        final int changed = jobs.markSucceeded(
                jobId, token, now, JobStatus.RUNNING, JobStatus.SUCCEEDED
        );
        if (changed == 0) {
            return false;
        }

        final long monsterId = jobs.findMonsterIdByJobId(jobId)
                .orElseThrow(() -> new IllegalStateException("결과 대상 Job이 없습니다: job_id=" + jobId));
        results.updateImage(monsterId, image);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(final Job claim, final Throwable cause) {
        final Instant now = now();
        final String reason = failureReason(cause);
        if (claim.attemptCount() >= settings.maxAttempts()) {
            return jobs.finishFailure(
                    claim.jobId(), claim.claimToken(), claim.attemptCount(), now, reason,
                    JobStatus.RUNNING, JobStatus.FAILED
            ) == 1;
        }
        return jobs.retryFailure(
                claim.jobId(), claim.claimToken(), claim.attemptCount(), now,
                now.plus(settings.retryDelay()), reason, JobStatus.RUNNING, JobStatus.PENDING
        ) == 1;
    }

    @Transactional(readOnly = true)
    public Optional<Job> findJob(final long jobId) {
        return jobs.findSnapshotById(jobId).map(JobProjection::toJob);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    static String failureReason(final Throwable cause) {
        final String reason = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        return reason.substring(0, Math.min(reason.length(), 2000));
    }

}
