package com.kng0501.dbqueue.application;

import static org.junit.jupiter.api.Assertions.*;

import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.JobStatus;
import com.kng0501.dbqueue.domain.QueueSettings;
import com.kng0501.dbqueue.support.MutableClock;
import com.kng0501.dbqueue.support.QueueTestDatabase;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class JobQueueTest {
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
    void 등록은_Monster와_PENDING_Job을_함께_저장한다() {
        var registered = queue.request("dragon");
        Job job = job(registered.jobId());

        assertAll(
                () -> assertEquals(1, db.count("queue_monster")),
                () -> assertEquals(1, db.count("image_generation_job")),
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
        db.jdbc.execute("ALTER TABLE image_generation_job ADD CONSTRAINT reject_job CHECK (prompt <> 'enqueue-fail')");

        assertThrows(DataAccessException.class, () -> queue.request("enqueue-fail"));

        assertEquals(0, db.count("queue_monster"));
        assertEquals(0, db.count("image_generation_job"));
    }

    @Test
    void Monster_INSERT가_실패하면_Job도_저장하지_않는다() {
        db.jdbc.execute("ALTER TABLE queue_monster ADD CONSTRAINT reject_monster CHECK (prompt <> 'save-fail')");

        assertThrows(DataAccessException.class, () -> queue.request("save-fail"));

        assertEquals(0, db.count("queue_monster"));
        assertEquals(0, db.count("image_generation_job"));
    }

    @Test
    void 같은_후보를_읽은_두_Worker_중_조건부_UPDATE가_성공한_하나만_선점한다() throws Exception {
        var registered = queue.request("dragon");
        var otherQueue = new JobQueue(db.dataSource, clock, settings);
        long firstCandidate = queue.findCandidate().orElseThrow();
        long secondCandidate = otherQueue.findCandidate().orElseThrow();
        assertEquals(firstCandidate, secondCandidate);
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                barrier.await(3, TimeUnit.SECONDS);
                return queue.tryClaim(firstCandidate);
            });
            var second = executor.submit(() -> {
                barrier.await(3, TimeUnit.SECONDS);
                return otherQueue.tryClaim(secondCandidate);
            });
            Optional<Job> a = first.get(5, TimeUnit.SECONDS);
            Optional<Job> b = second.get(5, TimeUnit.SECONDS);
            assertEquals(1, (a.isPresent() ? 1 : 0) + (b.isPresent() ? 1 : 0));
            Job claimed = job(registered.jobId());
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
        db.jdbc.update("INSERT INTO queue_monster(prompt) VALUES ('unrelated')");
        long unrelatedId = db.jdbc.queryForObject(
                "SELECT monster_id FROM queue_monster WHERE prompt = 'unrelated'", Long.class
        );
        var registered = queue.request("dragon");
        assertNotEquals(registered.jobId(), registered.monsterId());
        Job claim = queue.tryClaim(registered.jobId()).orElseThrow();

        assertTrue(queue.complete(claim.jobId(), claim.claimToken(), "image:dragon"));

        assertEquals("image:dragon", queue.findMonster(registered.monsterId()).orElseThrow().image());
        assertNull(queue.findMonster(unrelatedId).orElseThrow().image());
        assertEquals(JobStatus.SUCCEEDED, job(claim.jobId()).status());
        assertEquals(clock.instant(), job(claim.jobId()).finishedAt());
        assertEquals(1, db.count("image_generation_job"));
    }

    @Test
    void Monster_UPDATE가_실패하면_SUCCEEDED_전환도_롤백한다() {
        Job claim = claim("dragon");
        db.jdbc.execute("ALTER TABLE queue_monster ADD CONSTRAINT reject_image CHECK (image IS NULL)");

        assertThrows(DataAccessException.class, () -> queue.complete(claim.jobId(), claim.claimToken(), "image:dragon"));

        assertEquals(JobStatus.RUNNING, job(claim.jobId()).status());
        assertEquals(claim.claimToken(), job(claim.jobId()).claimToken());
        assertNull(job(claim.jobId()).finishedAt());
        assertNull(queue.findMonster(claim.monsterId()).orElseThrow().image());

        db.jdbc.execute("ALTER TABLE queue_monster DROP CONSTRAINT reject_image");
        assertTrue(queue.complete(claim.jobId(), claim.claimToken(), "image:dragon"));
        assertEquals(JobStatus.SUCCEEDED, job(claim.jobId()).status());
        assertEquals("image:dragon", queue.findMonster(claim.monsterId()).orElseThrow().image());
    }

    @Test
    void 결과_대상_행이_없어도_SUCCEEDED_전환을_롤백한다() {
        Job claim = claim("dragon");
        // 정상 FK로는 불가능한 데이터 손상을 주입해 UPDATE 0행 방어를 확인한다.
        db.jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        db.jdbc.update("DELETE FROM queue_monster WHERE monster_id = ?", claim.monsterId());

        assertThrows(IllegalStateException.class, () -> queue.complete(claim.jobId(), claim.claimToken(), "image"));

        assertEquals(JobStatus.RUNNING, job(claim.jobId()).status());
        assertEquals(claim.claimToken(), job(claim.jobId()).claimToken());
    }

    @Test
    void 완료_중복과_완료된_작업의_실패_요청을_거부한다() {
        Job claim = claim("dragon");

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
        Job claim = claim("dragon");
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                barrier.await(3, TimeUnit.SECONDS);
                return queue.complete(claim.jobId(), claim.claimToken(), "first");
            });
            var second = executor.submit(() -> {
                barrier.await(3, TimeUnit.SECONDS);
                return queue.complete(claim.jobId(), claim.claimToken(), "second");
            });
            boolean a = first.get(5, TimeUnit.SECONDS);
            boolean b = second.get(5, TimeUnit.SECONDS);
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
        Job a = claim("dragon");
        assertFalse(queue.complete(a.jobId(), UUID.randomUUID(), "wrong-token"));

        clock.advance(settings.processingTimeout());
        assertFalse(queue.complete(a.jobId(), a.claimToken(), "expired"));
        assertFalse(queue.fail(a, new IllegalStateException("expired failure")));
        assertEquals(1, queue.recoverExpired());
        clock.advance(settings.retryDelay());
        Job b = queue.tryClaim(a.jobId()).orElseThrow();
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
        Job claim = claim("dragon");
        clock.advance(settings.processingTimeout().minusNanos(1));
        assertEquals(0, queue.recoverExpired());
        assertEquals(JobStatus.RUNNING, job(claim.jobId()).status());

        clock.advance(Duration.ofNanos(1));
        assertEquals(1, queue.recoverExpired());
        Job pending = job(claim.jobId());
        assertEquals(JobStatus.PENDING, pending.status());
        assertEquals(1, pending.attemptCount());
        assertEquals(clock.instant().plus(settings.retryDelay()), pending.nextAttemptAt());
        assertNull(pending.claimToken());
        assertNull(pending.deadlineAt());
        assertNull(pending.finishedAt());
        assertEquals(0, queue.recoverExpired());
    }

    @Test
    void 기한_직전의_완료는_허용한다() {
        Job claim = claim("dragon");
        clock.advance(settings.processingTimeout().minusNanos(1));

        assertTrue(queue.complete(claim.jobId(), claim.claimToken(), "image"));
        assertEquals(0, queue.recoverExpired());
    }

    @Test
    void 실패_재시도는_간격을_지키고_최초_실행을_포함한_최대_시도에서_종료한다() {
        Job current = claim("dragon");
        for (int attempt = 1; attempt <= settings.maxAttempts(); attempt++) {
            assertEquals(attempt, current.attemptCount());
            assertTrue(queue.fail(current, new IllegalStateException("generation failed")));
            Job failedAttempt = job(current.jobId());
            assertNull(failedAttempt.claimToken());
            assertNull(failedAttempt.deadlineAt());
            assertTrue(failedAttempt.lastError().contains("generation failed"));
            if (attempt < settings.maxAttempts()) {
                assertEquals(JobStatus.PENDING, failedAttempt.status());
                clock.advance(settings.retryDelay().minusNanos(1));
                assertTrue(queue.findCandidate().isEmpty());
                assertTrue(queue.tryClaim(current.jobId()).isEmpty());
                clock.advance(Duration.ofNanos(1));
                assertEquals(current.jobId(), queue.findCandidate().orElseThrow());
                current = queue.tryClaim(current.jobId()).orElseThrow();
            }
        }

        Job ended = job(current.jobId());
        assertEquals(JobStatus.FAILED, ended.status());
        assertEquals(clock.instant(), ended.finishedAt());
        clock.advance(Duration.ofDays(1));
        assertTrue(queue.findCandidate().isEmpty());
        assertTrue(queue.tryClaim(ended.jobId()).isEmpty());
        assertEquals(0, queue.recoverExpired());
        assertEquals(1, db.count("image_generation_job"));
    }

    @Test
    void 마지막_시도의_타임아웃은_FAILED로_종료한다() {
        var oneAttempt = new QueueSettings(settings.processingTimeout(), 1, settings.retryDelay(),
                1, settings.pollingInterval(), settings.recoveryInterval());
        queue = new JobQueue(db.dataSource, clock, oneAttempt);
        Job claim = claim("dragon");

        clock.advance(settings.processingTimeout());
        assertEquals(1, queue.recoverExpired());

        Job ended = job(claim.jobId());
        assertEquals(JobStatus.FAILED, ended.status());
        assertEquals(1, ended.attemptCount());
        assertEquals(clock.instant(), ended.finishedAt());
        assertNull(ended.claimToken());
        assertNull(ended.deadlineAt());
        assertTrue(queue.findCandidate().isEmpty());
    }

    private Job claim(String prompt) {
        return queue.tryClaim(queue.request(prompt).jobId()).orElseThrow();
    }

    private Job job(long jobId) {
        return queue.findJob(jobId).orElseThrow();
    }
}
