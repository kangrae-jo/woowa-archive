package com.kng0501.dbqueue.application;

import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.Monster;
import com.kng0501.dbqueue.domain.QueueSettings;
import com.kng0501.dbqueue.persistence.JdbcJobRepository;
import com.kng0501.dbqueue.persistence.JdbcMonsterRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public final class JobQueue {
    private static final Logger LOG = LoggerFactory.getLogger(JobQueue.class);
    private static final int RECOVERY_BATCH_SIZE = 100;

    private final Clock clock;
    private final QueueSettings settings;
    private final JdbcMonsterRepository monsters;
    private final JdbcJobRepository jobs;
    private final TransactionTemplate transactions;

    public JobQueue(final DataSource dataSource, final Clock clock, final QueueSettings settings) {
        this.clock = Objects.requireNonNull(clock);
        this.settings = Objects.requireNonNull(settings);
        final var jdbc = new JdbcTemplate(dataSource);
        this.monsters = new JdbcMonsterRepository(jdbc);
        this.jobs = new JdbcJobRepository(jdbc);
        this.transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
        // 호출자 트랜잭션이 있어도 선점이 커밋된 뒤에만 Generator에 넘긴다.
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Registration request(final String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt는 비어 있을 수 없습니다.");
        }
        return transactions.execute(status -> {
            final Instant now = clock.instant();
            final long monsterId = monsters.save(prompt);
            final long jobId = jobs.enqueue(monsterId, prompt, now);
            return new Registration(jobId, monsterId);
        });
    }

    public Optional<Long> findCandidate() {
        return jobs.findCandidate(clock.instant(), settings.maxAttempts());
    }

    public Optional<Job> tryClaim(final long jobId) {
        return transactions.execute(status -> {
            final Instant now = clock.instant();
            final UUID token = UUID.randomUUID();
            if (!jobs.claim(jobId, token, now, now.plus(settings.processingTimeout()), settings.maxAttempts())) {
                return Optional.empty();
            }
            return Optional.of(jobs.findById(jobId).orElseThrow());
        });
    }

    public boolean complete(final long jobId, final UUID token, final String image) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            if (!jobs.markSucceeded(jobId, token, clock.instant())) {
                return false;
            }
            // 조건부 전환으로 잠근 행에서 연결 키를 읽는다. 호출자가 준 Monster ID는 사용하지 않는다.
            final Job job = jobs.findById(jobId).orElseThrow();
            if (monsters.updateImage(job.monsterId(), image) != 1) {
                throw new IllegalStateException("결과 갱신 대상 Monster가 없습니다: job_id=" + jobId);
            }
            return true;
        }));
    }

    public boolean fail(final Job claim, final Throwable cause) {
        final String reason = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        final String storedReason = reason.substring(0, Math.min(reason.length(), 2000));
        return Boolean.TRUE.equals(transactions.execute(status -> {
            final Instant now = clock.instant();
            return jobs.fail(claim, now, now.plus(settings.retryDelay()), settings.maxAttempts(), storedReason);
        }));
    }

    public int recoverExpired() {
        int recovered = 0;
        for (final Job expired : jobs.findExpired(clock.instant(), RECOVERY_BATCH_SIZE)) {
            try {
                final boolean changed = Boolean.TRUE.equals(transactions.execute(status -> {
                    final Instant now = clock.instant();
                    return jobs.expire(expired, now, now.plus(settings.retryDelay()), settings.maxAttempts());
                }));
                if (changed) {
                    recovered++;
                    LOG.warn("job_id={} attempt_count={} cause=processing deadline expired",
                            expired.jobId(), expired.attemptCount());
                }
            } catch (final RuntimeException failure) {
                LOG.error("job_id={} attempt_count={} cause=timeout recovery persistence failed",
                        expired.jobId(), expired.attemptCount(), failure);
            }
        }
        return recovered;
    }

    public Optional<Job> findJob(final long jobId) {
        return jobs.findById(jobId);
    }

    public Optional<Monster> findMonster(final long monsterId) {
        return monsters.findById(monsterId);
    }

    public record Registration(long jobId, long monsterId) {
    }
}
