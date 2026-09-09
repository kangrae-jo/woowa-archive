package com.kng0501.dbqueue.application;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.cleanHardened;
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

import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.JobStatus;
import com.kng0501.dbqueue.domain.QueueSettings;
import com.kng0501.dbqueue.support.MutableClock;
import com.kng0501.technicalwriting.testsupport.HardenedIntegrationTest;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@HardenedIntegrationTest
final class JobQueueTest {

    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final QueueSettings settings;
    private final JobQueue queue;
    private final ExpiredJobRecovery recovery;

    @Autowired
    JobQueueTest(
            final JdbcTemplate jdbc,
            final MutableClock clock,
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
        cleanHardened(jdbc);
        clock.reset();
    }

    @AfterEach
    void tearDown() {
        dropFaultConstraints();
        cleanHardened(jdbc);
        clock.reset();
    }

    @Test
    void 등록은_Monster와_PENDING_Job을_함께_저장한다() {
        final JobQueue.Registration registered = queue.request("dragon");
        final Job job = job(registered.jobId());

        assertAll(
                () -> assertEquals(1, count(jdbc, "queue_monster")),
                () -> assertEquals(1, count(jdbc, "image_generation_job")),
                () -> assertEquals(registered.monsterId(), job.monsterId()),
                () -> assertEquals(JobStatus.PENDING, job.status()),
                () -> assertEquals(0, job.attemptCount()),
                () -> assertEquals(clock.instant(), job.createdAt()),
                () -> assertEquals(clock.instant(), job.nextAttemptAt()),
                () -> assertNull(job.claimToken()),
                () -> assertNull(job.deadlineAt())
        );
    }

    @Test
    void Job_INSERT가_실패하면_먼저_저장한_Monster도_롤백한다() {
        jdbc.execute("ALTER TABLE image_generation_job ADD CONSTRAINT reject_job "
                + "CHECK (prompt <> 'enqueue-fail')");
        try {
            assertThrows(DataIntegrityViolationException.class, () -> queue.request("enqueue-fail"));
        } finally {
            dropCheckIfExists(jdbc, "image_generation_job", "reject_job");
        }

        assertEquals(0, count(jdbc, "queue_monster"));
        assertEquals(0, count(jdbc, "image_generation_job"));
    }

    @Test
    void Monster_INSERT가_실패하면_Job도_저장하지_않는다() {
        jdbc.execute("ALTER TABLE queue_monster ADD CONSTRAINT reject_monster "
                + "CHECK (prompt <> 'save-fail')");
        try {
            assertThrows(DataIntegrityViolationException.class, () -> queue.request("save-fail"));
        } finally {
            dropCheckIfExists(jdbc, "queue_monster", "reject_monster");
        }

        assertEquals(0, count(jdbc, "queue_monster"));
        assertEquals(0, count(jdbc, "image_generation_job"));
    }

    @Test
    void 같은_후보를_읽은_두_Worker_중_조건부_UPDATE가_성공한_하나만_선점한다() throws Exception {
        final JobQueue.Registration registered = queue.request("dragon");
        final long firstCandidate = queue.findCandidate().orElseThrow();
        final long secondCandidate = queue.findCandidate().orElseThrow();
        assertEquals(firstCandidate, secondCandidate);
        final var barrier = new CyclicBarrier(2);
        final var executor = Executors.newFixedThreadPool(2);
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
            assertEquals(JobStatus.RUNNING, claimed.status());
            assertEquals(1, claimed.attemptCount());
            assertEquals(clock.instant(), claimed.startedAt());
            assertEquals(clock.instant().plus(settings.processingTimeout()), claimed.deadlineAt());
            assertNotNull(claimed.claimToken());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void 결과는_Job_ID가_아닌_명시적_Monster_FK로_연결한다() {
        jdbc.update("INSERT INTO queue_monster(prompt) VALUES ('unrelated')");
        final long unrelatedId = jdbc.queryForObject(
                "SELECT monster_id FROM queue_monster WHERE prompt = 'unrelated'", Long.class
        );
        final JobQueue.Registration registered = queue.request("dragon");
        assertNotEquals(registered.jobId(), registered.monsterId());
        final Job claim = queue.tryClaim(registered.jobId()).orElseThrow();

        assertTrue(queue.complete(claim.jobId(), claim.claimToken(), "image:dragon"));

        assertEquals("image:dragon", queue.findMonster(registered.monsterId()).orElseThrow().image());
        assertNull(queue.findMonster(unrelatedId).orElseThrow().image());
        assertEquals(JobStatus.SUCCEEDED, job(claim.jobId()).status());
        assertEquals(clock.instant(), job(claim.jobId()).finishedAt());
        assertEquals(1, count(jdbc, "image_generation_job"));
    }

    @Test
    void Monster_UPDATE가_실패하면_SUCCEEDED_전환도_롤백한다() {
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
        assertNull(job(claim.jobId()).finishedAt());
        assertNull(queue.findMonster(claim.monsterId()).orElseThrow().image());

        assertTrue(queue.complete(claim.jobId(), claim.claimToken(), "image:dragon"));
        assertEquals(JobStatus.SUCCEEDED, job(claim.jobId()).status());
        assertEquals("image:dragon", queue.findMonster(claim.monsterId()).orElseThrow().image());
    }

    @Test
    void 결과_대상_행이_없어도_SUCCEEDED_전환을_롤백한다() {
        final Job claim = claim("dragon");
        deleteMonsterWithoutForeignKeyCheck(jdbc, claim.monsterId());

        assertThrows(
                IllegalStateException.class,
                () -> queue.complete(claim.jobId(), claim.claimToken(), "image")
        );

        assertEquals(JobStatus.RUNNING, job(claim.jobId()).status());
        assertEquals(claim.claimToken(), job(claim.jobId()).claimToken());
    }

    @Test
    void 완료_중복과_완료된_작업의_실패_요청을_거부한다() {
        final Job claim = claim("dragon");

        assertTrue(queue.complete(claim.jobId(), claim.claimToken(), "first"));
        assertFalse(queue.complete(claim.jobId(), claim.claimToken(), "duplicate"));
        assertFalse(queue.fail(claim, new IllegalStateException("late failure")));

        assertEquals("first", queue.findMonster(claim.monsterId()).orElseThrow().image());
        assertEquals(JobStatus.SUCCEEDED, job(claim.jobId()).status());
        assertNull(job(claim.jobId()).claimToken());
        assertNull(job(claim.jobId()).deadlineAt());
        assertTrue(queue.findCandidate().isEmpty());
        assertTrue(queue.tryClaim(claim.jobId()).isEmpty());
    }

    @Test
    void 같은_토큰의_동시_완료도_한_결과만_반영한다() throws Exception {
        final Job claim = claim("dragon");
        final var barrier = new CyclicBarrier(2);
        final var executor = Executors.newFixedThreadPool(2);
        try {
            final var first = executor.submit(() -> {
                barrier.await(3, TimeUnit.SECONDS);
                return queue.complete(claim.jobId(), claim.claimToken(), "first");
            });
            final var second = executor.submit(() -> {
                barrier.await(3, TimeUnit.SECONDS);
                return queue.complete(claim.jobId(), claim.claimToken(), "second");
            });
            final boolean a = first.get(5, TimeUnit.SECONDS);
            final boolean b = second.get(5, TimeUnit.SECONDS);
            assertNotEquals(a, b);
            assertEquals(a ? "first" : "second", queue.findMonster(claim.monsterId()).orElseThrow().image());
            assertEquals(JobStatus.SUCCEEDED, job(claim.jobId()).status());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void 잘못된_토큰과_재선점_이전_Worker의_완료와_실패를_거부한다() {
        final Job a = claim("dragon");
        assertFalse(queue.complete(a.jobId(), UUID.randomUUID(), "wrong-token"));

        clock.advance(settings.processingTimeout());
        assertFalse(queue.complete(a.jobId(), a.claimToken(), "expired"));
        assertFalse(queue.fail(a, new IllegalStateException("expired failure")));
        assertEquals(1, recovery.recoverExpired());
        clock.advance(settings.retryDelay());
        final Job b = queue.tryClaim(a.jobId()).orElseThrow();
        assertNotEquals(a.claimToken(), b.claimToken());

        assertFalse(queue.complete(a.jobId(), a.claimToken(), "late-a"));
        assertFalse(queue.fail(a, new IllegalStateException("late-a failure")));
        assertEquals(b.claimToken(), job(a.jobId()).claimToken());
        assertEquals(2, job(a.jobId()).attemptCount());

        assertTrue(queue.complete(b.jobId(), b.claimToken(), "image:b"));
        assertFalse(queue.complete(a.jobId(), a.claimToken(), "later-a"));
        assertEquals("image:b", queue.findMonster(a.monsterId()).orElseThrow().image());
    }

    @Test
    void 기한_직전에는_복구하지_않고_정확히_기한부터_복구한다() {
        final Job claim = claim("dragon");
        clock.advance(settings.processingTimeout().minus(1, ChronoUnit.MICROS));
        assertEquals(0, recovery.recoverExpired());
        assertEquals(JobStatus.RUNNING, job(claim.jobId()).status());

        clock.advance(Duration.of(1, ChronoUnit.MICROS));
        assertEquals(1, recovery.recoverExpired());
        final Job pending = job(claim.jobId());
        assertEquals(JobStatus.PENDING, pending.status());
        assertEquals(1, pending.attemptCount());
        assertEquals(clock.instant().plus(settings.retryDelay()), pending.nextAttemptAt());
        assertNull(pending.claimToken());
        assertNull(pending.deadlineAt());
        assertNull(pending.finishedAt());
        assertEquals(0, recovery.recoverExpired());
    }

    @Test
    void 기한_직전의_완료는_허용한다() {
        final Job claim = claim("dragon");
        clock.advance(settings.processingTimeout().minus(1, ChronoUnit.MICROS));

        assertTrue(queue.complete(claim.jobId(), claim.claimToken(), "image"));
        assertEquals(0, recovery.recoverExpired());
    }

    @Test
    void 실패_재시도는_간격을_지키고_최초_실행을_포함한_최대_시도에서_종료한다() {
        Job current = claim("dragon");
        for (int attempt = 1; attempt <= settings.maxAttempts(); attempt++) {
            assertEquals(attempt, current.attemptCount());
            assertTrue(queue.fail(current, new IllegalStateException("generation failed")));
            final Job failedAttempt = job(current.jobId());
            assertNull(failedAttempt.claimToken());
            assertNull(failedAttempt.deadlineAt());
            assertTrue(failedAttempt.lastError().contains("generation failed"));
            if (attempt < settings.maxAttempts()) {
                assertEquals(JobStatus.PENDING, failedAttempt.status());
                clock.advance(settings.retryDelay().minus(1, ChronoUnit.MICROS));
                assertTrue(queue.findCandidate().isEmpty());
                assertTrue(queue.tryClaim(current.jobId()).isEmpty());
                clock.advance(Duration.of(1, ChronoUnit.MICROS));
                assertEquals(current.jobId(), queue.findCandidate().orElseThrow());
                current = queue.tryClaim(current.jobId()).orElseThrow();
            }
        }

        final Job ended = job(current.jobId());
        assertEquals(JobStatus.FAILED, ended.status());
        assertEquals(clock.instant(), ended.finishedAt());
        clock.advance(Duration.ofDays(1));
        assertTrue(queue.findCandidate().isEmpty());
        assertTrue(queue.tryClaim(ended.jobId()).isEmpty());
        assertEquals(0, recovery.recoverExpired());
        assertEquals(1, count(jdbc, "image_generation_job"));
    }

    @Test
    void 마지막_시도의_타임아웃은_FAILED로_종료한다() {
        Job current = claim("dragon");
        while (current.attemptCount() < settings.maxAttempts()) {
            assertTrue(queue.fail(current, new IllegalStateException("retry before timeout")));
            clock.advance(settings.retryDelay());
            current = queue.tryClaim(current.jobId()).orElseThrow();
        }

        clock.advance(settings.processingTimeout());
        assertEquals(1, recovery.recoverExpired());

        final Job ended = job(current.jobId());
        assertEquals(JobStatus.FAILED, ended.status());
        assertEquals(settings.maxAttempts(), ended.attemptCount());
        assertEquals(clock.instant(), ended.finishedAt());
        assertNull(ended.claimToken());
        assertNull(ended.deadlineAt());
        assertTrue(queue.findCandidate().isEmpty());
    }

    private Job claim(final String prompt) {
        return queue.tryClaim(queue.request(prompt).jobId()).orElseThrow();
    }

    private Job job(final long jobId) {
        return queue.findJob(jobId).orElseThrow();
    }

    private void dropFaultConstraints() {
        dropCheckIfExists(jdbc, "image_generation_job", "reject_job");
        dropCheckIfExists(jdbc, "queue_monster", "reject_monster");
        dropCheckIfExists(jdbc, "queue_monster", "reject_image");
    }
}
