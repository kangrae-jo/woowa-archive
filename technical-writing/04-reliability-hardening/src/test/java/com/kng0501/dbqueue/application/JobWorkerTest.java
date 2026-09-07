package com.kng0501.dbqueue.application;

import static org.junit.jupiter.api.Assertions.*;

import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.JobStatus;
import com.kng0501.dbqueue.domain.QueueSettings;
import com.kng0501.dbqueue.support.MutableClock;
import com.kng0501.dbqueue.support.QueueTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class JobWorkerTest {
    private QueueTestDatabase db;
    private MutableClock clock;
    private QueueSettings settings;
    private JobQueue queue;

    @BeforeEach
    void setUp() {
        db = new QueueTestDatabase();
        clock = new MutableClock();
        settings = QueueSettings.experimentalDefaults();
        queue = new JobQueue(db.dataSource, clock, settings);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void Generator는_선점_커밋_후_DB_트랜잭션_없이_실행한다() {
        final Job claim = queue.tryClaim(queue.request("dragon").jobId()).orElseThrow();
        final var worker = new JobWorker(queue, prompt -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.hasResource(db.dataSource));
            assertEquals(JobStatus.RUNNING, queue.findJob(claim.jobId()).orElseThrow().status());
            return "image:" + prompt;
        });

        worker.execute(claim);

        assertEquals(JobStatus.SUCCEEDED, queue.findJob(claim.jobId()).orElseThrow().status());
        assertEquals("image:dragon", queue.findMonster(claim.monsterId()).orElseThrow().image());
    }

    @Test
    void 실패를_DB에_기록하지_못해도_RUNNING을_유지해_기한_복구할_수_있다() {
        final Job claim = queue.tryClaim(queue.request("dragon").jobId()).orElseThrow();
        db.jdbc.execute("ALTER TABLE image_generation_job ADD CONSTRAINT reject_failure CHECK (last_error IS NULL)");
        final var worker = new JobWorker(queue, prompt -> {
            throw new IllegalStateException("generator failed");
        });

        assertDoesNotThrow(() -> worker.execute(claim));

        final Job running = queue.findJob(claim.jobId()).orElseThrow();
        assertEquals(JobStatus.RUNNING, running.status());
        assertEquals(claim.claimToken(), running.claimToken());
        assertEquals(claim.deadlineAt(), running.deadlineAt());
        assertNull(running.lastError());

        db.jdbc.execute("ALTER TABLE image_generation_job DROP CONSTRAINT reject_failure");
        clock.advance(settings.processingTimeout());
        assertEquals(1, queue.recoverExpired());
        clock.advance(settings.retryDelay());
        final Job retry = queue.tryClaim(claim.jobId()).orElseThrow();
        new JobWorker(queue, prompt -> "recovered").execute(retry);
        assertEquals(JobStatus.SUCCEEDED, queue.findJob(claim.jobId()).orElseThrow().status());
        assertEquals("recovered", queue.findMonster(claim.monsterId()).orElseThrow().image());
    }

    @Test
    void 한_Job의_복구_저장_실패가_다른_만료_Job의_복구를_막지_않는다() {
        final Job first = queue.tryClaim(queue.request("first").jobId()).orElseThrow();
        final Job second = queue.tryClaim(queue.request("second").jobId()).orElseThrow();
        db.jdbc.execute("ALTER TABLE image_generation_job ADD CONSTRAINT reject_first_failure "
                + "CHECK (CASE WHEN job_id = " + first.jobId() + " THEN last_error IS NULL ELSE TRUE END)");
        clock.advance(settings.processingTimeout());

        assertEquals(1, queue.recoverExpired());

        assertEquals(JobStatus.RUNNING, queue.findJob(first.jobId()).orElseThrow().status());
        assertEquals(JobStatus.PENDING, queue.findJob(second.jobId()).orElseThrow().status());
        db.jdbc.execute("ALTER TABLE image_generation_job DROP CONSTRAINT reject_first_failure");
        assertEquals(1, queue.recoverExpired());
    }
}
