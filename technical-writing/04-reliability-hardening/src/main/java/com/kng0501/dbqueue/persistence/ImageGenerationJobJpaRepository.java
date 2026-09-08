package com.kng0501.dbqueue.persistence;

import com.kng0501.dbqueue.domain.JobStatus;
import com.kng0501.dbqueue.persistence.entity.ImageGenerationJobEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface ImageGenerationJobJpaRepository extends JpaRepository<ImageGenerationJobEntity, Long> {

    @Query("""
            select job.id
            from ImageGenerationJobEntity job
            where job.status = :pending
              and job.nextAttemptAt <= :now
              and job.attemptCount < :maxAttempts
            order by job.nextAttemptAt, job.id
            """)
    List<Long> findCandidates(
            @Param("pending") JobStatus pending,
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ImageGenerationJobEntity job
               set job.status = :running,
                   job.claimToken = :token,
                   job.deadlineAt = :deadline,
                   job.attemptCount = job.attemptCount + 1,
                   job.startedAt = :now,
                   job.finishedAt = null
             where job.id = :jobId
               and job.status = :pending
               and job.nextAttemptAt <= :now
               and job.attemptCount < :maxAttempts
            """)
    int claim(
            @Param("jobId") long jobId,
            @Param("token") UUID token,
            @Param("now") Instant now,
            @Param("deadline") Instant deadline,
            @Param("maxAttempts") int maxAttempts,
            @Param("pending") JobStatus pending,
            @Param("running") JobStatus running
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ImageGenerationJobEntity job
               set job.status = :succeeded,
                   job.finishedAt = :now,
                   job.claimToken = null,
                   job.deadlineAt = null
             where job.id = :jobId
               and job.status = :running
               and job.claimToken = :token
               and job.deadlineAt > :now
            """)
    int markSucceeded(
            @Param("jobId") long jobId,
            @Param("token") UUID token,
            @Param("now") Instant now,
            @Param("running") JobStatus running,
            @Param("succeeded") JobStatus succeeded
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ImageGenerationJobEntity job
               set job.status = :pending,
                   job.nextAttemptAt = :nextAttemptAt,
                   job.finishedAt = null,
                   job.claimToken = null,
                   job.deadlineAt = null,
                   job.lastError = :reason
             where job.id = :jobId
               and job.status = :running
               and job.claimToken = :token
               and job.attemptCount = :attemptCount
               and job.deadlineAt > :now
            """)
    int retryFailure(
            @Param("jobId") long jobId,
            @Param("token") UUID token,
            @Param("attemptCount") int attemptCount,
            @Param("now") Instant now,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("reason") String reason,
            @Param("running") JobStatus running,
            @Param("pending") JobStatus pending
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ImageGenerationJobEntity job
               set job.status = :failed,
                   job.finishedAt = :now,
                   job.claimToken = null,
                   job.deadlineAt = null,
                   job.lastError = :reason
             where job.id = :jobId
               and job.status = :running
               and job.claimToken = :token
               and job.attemptCount = :attemptCount
               and job.deadlineAt > :now
            """)
    int finishFailure(
            @Param("jobId") long jobId,
            @Param("token") UUID token,
            @Param("attemptCount") int attemptCount,
            @Param("now") Instant now,
            @Param("reason") String reason,
            @Param("running") JobStatus running,
            @Param("failed") JobStatus failed
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ImageGenerationJobEntity job
               set job.status = :pending,
                   job.nextAttemptAt = :nextAttemptAt,
                   job.finishedAt = null,
                   job.claimToken = null,
                   job.deadlineAt = null,
                   job.lastError = :reason
             where job.id = :jobId
               and job.status = :running
               and job.claimToken = :token
               and job.attemptCount = :attemptCount
               and job.deadlineAt <= :now
            """)
    int retryExpired(
            @Param("jobId") long jobId,
            @Param("token") UUID token,
            @Param("attemptCount") int attemptCount,
            @Param("now") Instant now,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("reason") String reason,
            @Param("running") JobStatus running,
            @Param("pending") JobStatus pending
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ImageGenerationJobEntity job
               set job.status = :failed,
                   job.finishedAt = :now,
                   job.claimToken = null,
                   job.deadlineAt = null,
                   job.lastError = :reason
             where job.id = :jobId
               and job.status = :running
               and job.claimToken = :token
               and job.attemptCount = :attemptCount
               and job.deadlineAt <= :now
            """)
    int finishExpired(
            @Param("jobId") long jobId,
            @Param("token") UUID token,
            @Param("attemptCount") int attemptCount,
            @Param("now") Instant now,
            @Param("reason") String reason,
            @Param("running") JobStatus running,
            @Param("failed") JobStatus failed
    );

    @Query("""
            select job.id as jobId,
                   job.monster.id as monsterId,
                   job.prompt as prompt,
                   job.status as status,
                   job.attemptCount as attemptCount,
                   job.nextAttemptAt as nextAttemptAt,
                   job.deadlineAt as deadlineAt,
                   job.claimToken as claimToken,
                   job.createdAt as createdAt,
                   job.startedAt as startedAt,
                   job.finishedAt as finishedAt,
                   job.lastError as lastError
              from ImageGenerationJobEntity job
             where job.id = :jobId
            """)
    Optional<JobProjection> findSnapshotById(@Param("jobId") long jobId);

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    @Query("""
            select job.id as jobId,
                   job.monster.id as monsterId,
                   job.prompt as prompt,
                   job.status as status,
                   job.attemptCount as attemptCount,
                   job.nextAttemptAt as nextAttemptAt,
                   job.deadlineAt as deadlineAt,
                   job.claimToken as claimToken,
                   job.createdAt as createdAt,
                   job.startedAt as startedAt,
                   job.finishedAt as finishedAt,
                   job.lastError as lastError
              from ImageGenerationJobEntity job
             where job.status = :running
               and job.deadlineAt <= :now
             order by job.deadlineAt, job.id
            """)
    List<JobProjection> findExpired(
            @Param("running") JobStatus running,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("select job.monster.id from ImageGenerationJobEntity job where job.id = :jobId")
    Optional<Long> findMonsterIdByJobId(@Param("jobId") long jobId);

    @Query("select job from ImageGenerationJobEntity job order by job.id")
    List<ImageGenerationJobEntity> findAllForQueryPlan();
}
