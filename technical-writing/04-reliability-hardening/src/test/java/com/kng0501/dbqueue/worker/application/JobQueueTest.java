package com.kng0501.dbqueue.worker.application;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.clean04;
import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.count;
import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.deleteMonsterWithoutForeignKeyCheck;
import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.dropCheckIfExists;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbqueue.worker.TestClock;
import com.kng0501.dbqueue.worker.WorkerIntegrationTest;
import com.kng0501.dbqueue.worker.WorkerTestData;
import com.kng0501.dbqueue.worker.domain.Job;
import com.kng0501.dbqueue.worker.domain.JobStatus;
import com.kng0501.dbqueue.worker.domain.QueueSettings;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@WorkerIntegrationTest
final class JobQueueTest {

    private final JdbcTemplate jdbc;
    private final TestClock clock;
    private final QueueSettings settings;
    private final JobQueue queue;
    private final ExpiredJobRecovery recovery;

    @Autowired
    JobQueueTest(
            final JdbcTemplate jdbc,
            final TestClock clock,
            final QueueSettings settings,
            final JobQueue queue,
            final ExpiredJobRecovery recovery
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
        this.queue = queue;
        this.recovery = recovery;
    }

    @BeforeEach
    void setUp() {
        dropFaultConstraints();
        clean04(jdbc);
        clock.reset();
    }

    @AfterEach
    void tearDown() {
        dropFaultConstraints();
        clean04(jdbc);
        clock.reset();
    }

    @Test
    void 같은_후보를_읽은_두_Worker_중_조건부_UPDATE가_성공한_하나만_선점한다() throws Exception {
        final WorkerTestData.JobData registered = register("dragon");
        final long firstCandidate = queue.findCandidate().orElseThrow();
        final long secondCandidate = queue.findCandidate().orElseThrow();
        assertEquals(firstCandidate, secondCandidate);

        final CyclicBarrier barrier = new CyclicBarrier(2);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final var first = executor.submit(() -> {
                barrier.await(3, TimeUnit.SECONDS);
                return queue.tryClaim(firstCandidate);
            });
            final var second = executor.submit(() -> {
                barrier.await(3, TimeUnit.SECONDS);
                return queue.tryClaim(secondCandidate);
            });
            final Optional<Job> a = first.get(5, TimeUnit.SECONDS);
            final Optional<Job> b = second.get(5, TimeUnit.SECONDS);

            assertEquals(1, (a.isPresent() ? 1 : 0) + (b.isPresent() ? 1 : 0));
            final Job claimed = job(registered.jobId());
            assertAll(
                    () -> assertEquals(JobStatus.RUNNING, claimed.status()),
                    () -> assertEquals(1, claimed.attemptCount()),
                    () -> assertEquals(clock.instant(), claimed.startedAt()),
                    () -> assertEquals(clock.instant().plus(settings.processingTimeout()), claimed.deadlineAt()),
                    () -> assertNotNull(claimed.claimToken())
            );
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void 결과는_Job_ID가_아닌_명시적_monsterId로_연결한다() {
        final long unrelatedMonsterId = WorkerTestData.insertMonster(jdbc, "unrelated");
        final WorkerTestData.JobData registered = register("dragon");
        assertNotEquals(registered.jobId(), registered.monsterId());
        final Job claim = queue.tryClaim(registered.jobId()).orElseThrow();

        assertTrue(queue.complete(claim.jobId(), claim.claimToken(), "image:dragon"));

        assertEquals("image:dragon", imageOf(registered.monsterId()));
        assertNull(imageOf(unrelatedMonsterId));
        assertEquals(JobStatus.SUCCEEDED, job(claim.jobId()).status());
        assertEquals(1, count(jdbc, "image_generation_job"));
    }

    @Test
    void Monster_결과_갱신이_실패하면_SUCCEEDED_전환도_롤백한다() {
        final Job claim = claim("dragon");
        jdbc.execute("ALTER TABLE queue_monster ADD CONSTRAINT reject_image CHECK (image IS NULL)");
        try {
            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> queue.complete(claim.jobId(), claim.claimToken(), "image:dragon")
            );
        } finally {
            dropCheckIfExists(jdbc, "queue_monster", "reject_image");
        }

        assertEquals(JobStatus.RUNNING, job(claim.jobId()).status());
        assertEquals(claim.claimToken(), job(claim.jobId()).claimToken());
        assertNull(imageOf(claim.monsterId()));

        assertTrue(queue.complete(claim.jobId(), claim.claimToken(), "image:dragon"));
        assertEquals(JobStatus.SUCCEEDED, job(claim.jobId()).status());
        assertEquals("image:dragon", imageOf(claim.monsterId()));
    }

    @Test
    void 결과_대상_행이_없어도_SUCCEEDED_전환을_롤백한다() {
        final Job claim = claim("dragon");
        deleteMonsterWithoutForeignKeyCheck(jdbc, claim.monsterId());

        assertThrows(IllegalStateException.class, () -> queue.complete(claim.jobId(), claim.claimToken(), "image"));

        assertEquals(JobStatus.RUNNING, job(claim.jobId()).status());
        assertEquals(claim.claimToken(), job(claim.jobId()).claimToken());
    }

    @Test
    void 완료_중복과_완료된_작업의_실패_요청을_거부한다() {
        final Job claim = claim("dragon");

        assertTrue(queue.complete(claim.jobId(), claim.claimToken(), "first"));
        assertFalse(queue.complete(claim.jobId(), claim.claimToken(), "duplicate"));
        assertFalse(queue.fail(claim, new IllegalStateException("late failure")));

        assertEquals("first", imageOf(claim.monsterId()));
        assertEquals(JobStatus.SUCCEEDED, job(claim.jobId()).status());
        assertNull(job(claim.jobId()).claimToken());
        assertTrue(queue.findCandidate().isEmpty());
    }

    @Test
    void 오래된_토큰은_재선점된_작업의_결과를_반영하지_못한다() {
        final Job first = claim("dragon");
        clock.advance(settings.processingTimeout());
        assertEquals(1, recovery.recoverExpired());
        clock.advance(settings.retryDelay());
        final Job second = queue.tryClaim(first.jobId()).orElseThrow();
        assertNotEquals(first.claimToken(), second.claimToken());

        assertFalse(queue.complete(first.jobId(), first.claimToken(), "late"));
        assertFalse(queue.fail(first, new IllegalStateException("late failure")));
        assertTrue(queue.complete(second.jobId(), second.claimToken(), "current"));

        assertEquals("current", imageOf(second.monsterId()));
        assertEquals(JobStatus.SUCCEEDED, job(second.jobId()).status());
    }

    @Test
    void 기한_직전에는_복구하지_않고_기한부터_재시도_대기로_전환한다() {
        final Job claim = claim("dragon");
        clock.advance(settings.processingTimeout().minus(1, ChronoUnit.MICROS));
        assertEquals(0, recovery.recoverExpired());
        assertEquals(JobStatus.RUNNING, job(claim.jobId()).status());

        clock.advance(Duration.of(1, ChronoUnit.MICROS));
        assertEquals(1, recovery.recoverExpired());
        final Job pending = job(claim.jobId());
        assertAll(
                () -> assertEquals(JobStatus.PENDING, pending.status()),
                () -> assertEquals(1, pending.attemptCount()),
                () -> assertEquals(clock.instant().plus(settings.retryDelay()), pending.nextAttemptAt()),
                () -> assertNull(pending.claimToken()),
                () -> assertNull(pending.deadlineAt())
        );
    }

    @Test
    void 실패_재시도는_간격을_지키고_최대_시도에서_FAILED로_종료한다() {
        Job current = claim("dragon");
        for (int attempt = 1; attempt <= settings.maxAttempts(); attempt++) {
            assertEquals(attempt, current.attemptCount());
            assertTrue(queue.fail(current, new IllegalStateException("generation failed")));
            final Job afterFailure = job(current.jobId());
            if (attempt < settings.maxAttempts()) {
                assertEquals(JobStatus.PENDING, afterFailure.status());
                assertTrue(queue.findCandidate().isEmpty());
                clock.advance(settings.retryDelay());
                current = queue.tryClaim(current.jobId()).orElseThrow();
            } else {
                assertEquals(JobStatus.FAILED, afterFailure.status());
                assertNull(afterFailure.claimToken());
                assertNull(afterFailure.deadlineAt());
            }
        }
        assertTrue(queue.findCandidate().isEmpty());
    }

    private WorkerTestData.JobData register(final String prompt) {
        return WorkerTestData.register(jdbc, prompt, clock.instant());
    }

    private Job claim(final String prompt) {
        final WorkerTestData.JobData registered = register(prompt);
        return queue.tryClaim(registered.jobId()).orElseThrow();
    }

    private Job job(final long jobId) {
        return queue.findJob(jobId).orElseThrow();
    }

    private String imageOf(final long monsterId) {
        return jdbc.queryForObject("SELECT image FROM queue_monster WHERE monster_id = ?", String.class, monsterId);
    }

    private void dropFaultConstraints() {
        dropCheckIfExists(jdbc, "queue_monster", "reject_image");
    }
}
