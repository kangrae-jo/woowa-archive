package com.kng0501.dbqueue.application;

import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.JobStatus;
import com.kng0501.dbqueue.domain.Monster;
import com.kng0501.dbqueue.domain.QueueSettings;
import com.kng0501.dbqueue.persistence.ImageGenerationJobJpaRepository;
import com.kng0501.dbqueue.persistence.JobProjection;
import com.kng0501.dbqueue.persistence.QueueMonsterJpaRepository;
import com.kng0501.dbqueue.persistence.entity.ImageGenerationJobEntity;
import com.kng0501.dbqueue.persistence.entity.QueueMonsterEntity;
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
    private final QueueMonsterJpaRepository monsters;
    private final ImageGenerationJobJpaRepository jobs;
    private final ExpiredJobRecovery expiredJobRecovery;

    public JobQueue(
            final Clock clock,
            final QueueSettings settings,
            final QueueMonsterJpaRepository monsters,
            final ImageGenerationJobJpaRepository jobs,
            final ExpiredJobRecovery expiredJobRecovery
    ) {
        this.clock = clock;
        this.settings = settings;
        this.monsters = monsters;
        this.jobs = jobs;
        this.expiredJobRecovery = expiredJobRecovery;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Registration request(final String prompt) {
        validatePrompt(prompt);
        final Instant now = now();
        final QueueMonsterEntity monster = monsters.save(new QueueMonsterEntity(prompt));
        final ImageGenerationJobEntity job = jobs.saveAndFlush(new ImageGenerationJobEntity(monster, prompt, now));
        return new Registration(job.getId(), monster.getId());
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
        final QueueMonsterEntity monster = monsters.findById(monsterId)
                .orElseThrow(() -> new IllegalStateException("결과 대상 Monster가 없습니다: job_id=" + jobId));
        monster.updateImage(image);
        monsters.flush();
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

    public int recoverExpired() {
        return expiredJobRecovery.recoverExpired();
    }

    @Transactional(readOnly = true)
    public Optional<Job> findJob(final long jobId) {
        return jobs.findSnapshotById(jobId).map(JobProjection::toJob);
    }

    @Transactional(readOnly = true)
    public Optional<Monster> findMonster(final long monsterId) {
        return monsters.findById(monsterId)
                .map(entity -> new Monster(entity.getId(), entity.getPrompt(), entity.getImage()));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    static String failureReason(final Throwable cause) {
        final String reason = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        return reason.substring(0, Math.min(reason.length(), 2000));
    }

    private static void validatePrompt(final String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt는 비어 있을 수 없습니다.");
        }
    }

    public record Registration(long jobId, long monsterId) {
    }
}
