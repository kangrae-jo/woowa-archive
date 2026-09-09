package com.kng0501.dbqueue.application;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.cleanHardened;
import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.dropCheckIfExists;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.JobStatus;
import com.kng0501.dbqueue.domain.QueueSettings;
import com.kng0501.dbqueue.support.MutableClock;
import com.kng0501.technicalwriting.testsupport.HardenedIntegrationTest;
import com.kng0501.technicalwriting.testsupport.HardenedTestImageGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@HardenedIntegrationTest
final class JobWorkerTest {

    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final QueueSettings settings;
    private final JobQueue queue;
    private final ExpiredJobRecovery recovery;
    private final JobWorker worker;
    private final HardenedTestImageGenerator generator;

    @Autowired
    JobWorkerTest(
            final JdbcTemplate jdbc,
            final MutableClock clock,
            final QueueSettings settings,
            final JobQueue queue,
            final ExpiredJobRecovery recovery,
            final JobWorker worker,
            final HardenedTestImageGenerator generator
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
        this.queue = queue;
        this.recovery = recovery;
        this.worker = worker;
        this.generator = generator;
    }

    @BeforeEach
    void setUp() {
        dropCheckIfExists(jdbc, "image_generation_job", "reject_failure");
        cleanHardened(jdbc);
        clock.reset();
        generator.reset();
    }

    @AfterEach
    void tearDown() {
        dropCheckIfExists(jdbc, "image_generation_job", "reject_failure");
        cleanHardened(jdbc);
        clock.reset();
        generator.reset();
    }

    @Test
    void Generator는_선점_커밋_후_DB_트랜잭션_없이_실행한다() {
        final Job claim = queue.tryClaim(queue.request("dragon").jobId()).orElseThrow();
        generator.use(prompt -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
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
        jdbc.execute("ALTER TABLE image_generation_job ADD CONSTRAINT reject_failure CHECK (last_error IS NULL)");
        generator.use(prompt -> {
            throw new IllegalStateException("generator failed");
        });

        assertDoesNotThrow(() -> worker.execute(claim));

        final Job running = queue.findJob(claim.jobId()).orElseThrow();
        assertEquals(JobStatus.RUNNING, running.status());
        assertEquals(claim.claimToken(), running.claimToken());
        assertEquals(claim.deadlineAt(), running.deadlineAt());
        assertNull(running.lastError());

        dropCheckIfExists(jdbc, "image_generation_job", "reject_failure");
        clock.advance(settings.processingTimeout());
        assertEquals(1, recovery.recoverExpired());
        clock.advance(settings.retryDelay());
        final Job retry = queue.tryClaim(claim.jobId()).orElseThrow();
        generator.reset();
        worker.execute(retry);
        assertEquals(JobStatus.SUCCEEDED, queue.findJob(claim.jobId()).orElseThrow().status());
        assertEquals("image:dragon", queue.findMonster(claim.monsterId()).orElseThrow().image());
    }

    @Test
    void 한_Job의_복구_저장_실패가_다른_만료_Job의_복구를_막지_않는다() {
        final Job first = queue.tryClaim(queue.request("first").jobId()).orElseThrow();
        final Job second = queue.tryClaim(queue.request("second").jobId()).orElseThrow();
        jdbc.execute("ALTER TABLE image_generation_job ADD CONSTRAINT reject_failure CHECK ("
                + "job_id <> " + first.jobId() + " OR last_error IS NULL)");
        clock.advance(settings.processingTimeout());

        assertEquals(1, recovery.recoverExpired());

        assertEquals(JobStatus.RUNNING, queue.findJob(first.jobId()).orElseThrow().status());
        assertEquals(JobStatus.PENDING, queue.findJob(second.jobId()).orElseThrow().status());
        dropCheckIfExists(jdbc, "image_generation_job", "reject_failure");
        assertEquals(1, recovery.recoverExpired());
    }
}
