package com.kng0501.dbqueue.worker.application;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.clean04;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbqueue.worker.TestClock;
import com.kng0501.dbqueue.worker.TestImageGenerator;
import com.kng0501.dbqueue.worker.WorkerIntegrationTest;
import com.kng0501.dbqueue.worker.WorkerTestData;
import com.kng0501.dbqueue.worker.domain.Job;
import com.kng0501.dbqueue.worker.domain.JobStatus;
import com.kng0501.dbqueue.worker.domain.QueueSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@WorkerIntegrationTest
final class JobWorkerTest {

    private final JdbcTemplate jdbc;
    private final TestClock clock;
    private final QueueSettings settings;
    private final JobQueue queue;
    private final JobWorker worker;
    private final TestImageGenerator generator;

    @Autowired
    JobWorkerTest(
            final JdbcTemplate jdbc,
            final TestClock clock,
            final QueueSettings settings,
            final JobQueue queue,
            final JobWorker worker,
            final TestImageGenerator generator
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
        this.queue = queue;
        this.worker = worker;
        this.generator = generator;
    }

    @BeforeEach
    void setUp() {
        clean04(jdbc);
        clock.reset();
        generator.reset();
    }

    @AfterEach
    void tearDown() {
        clean04(jdbc);
        clock.reset();
        generator.reset();
    }

    @Test
    void Generator는_선점_커밋_후_DB_트랜잭션_없이_실행한다() {
        final Job claim = claim("dragon");
        generator.use(prompt -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(JobStatus.RUNNING, queue.findJob(claim.jobId()).orElseThrow().status());
            return "image:" + prompt;
        });

        worker.execute(claim);

        assertEquals(JobStatus.SUCCEEDED, queue.findJob(claim.jobId()).orElseThrow().status());
        assertEquals("image:dragon", imageOf(claim.monsterId()));
    }

    @Test
    void Generator_실패는_재시도_대기로_기록되고_다음_작업을_위해_복구할_수_있다() {
        final Job claim = claim("dragon");
        generator.use(prompt -> {
            throw new IllegalStateException("generator failed");
        });

        assertDoesNotThrow(() -> worker.execute(claim));

        final Job pending = queue.findJob(claim.jobId()).orElseThrow();
        assertEquals(JobStatus.PENDING, pending.status());
        assertEquals(1, pending.attemptCount());
        assertTrue(pending.lastError().contains("generator failed"));

        clock.advance(settings.retryDelay());
        final Job retry = queue.tryClaim(claim.jobId()).orElseThrow();
        generator.reset();
        worker.execute(retry);

        assertEquals(JobStatus.SUCCEEDED, queue.findJob(claim.jobId()).orElseThrow().status());
        assertEquals("image:dragon", imageOf(claim.monsterId()));
    }

    private Job claim(final String prompt) {
        final WorkerTestData.JobData registered = WorkerTestData.register(jdbc, prompt, clock.instant());
        return queue.tryClaim(registered.jobId()).orElseThrow();
    }

    private String imageOf(final long monsterId) {
        return jdbc.queryForObject("SELECT image FROM queue_monster WHERE monster_id = ?", String.class, monsterId);
    }
}
