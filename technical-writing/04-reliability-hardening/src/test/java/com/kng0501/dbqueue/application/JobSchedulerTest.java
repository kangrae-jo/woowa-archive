package com.kng0501.dbqueue.application;

import static org.junit.jupiter.api.Assertions.*;

import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.JobStatus;
import com.kng0501.dbqueue.domain.QueueSettings;
import com.kng0501.dbqueue.support.MutableClock;
import com.kng0501.dbqueue.support.QueueTestDatabase;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobSchedulerTest {
    private QueueTestDatabase db;
    private MutableClock clock;
    private QueueSettings settings;
    private JobQueue queue;

    @BeforeEach
    void setUp() {
        db = new QueueTestDatabase();
        clock = new MutableClock();
        settings = new QueueSettings(
                Duration.ofSeconds(30), 3, Duration.ofSeconds(1), 2,
                Duration.ofMillis(10), Duration.ofMillis(10)
        );
        queue = new JobQueue(db.dataSource, clock, settings);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void 첫_작업_실패_이후_등록한_특정_Job의_완료와_이미지를_확인한다() throws Exception {
        var first = queue.request("first-fails");
        var firstAttempted = new CountDownLatch(1);
        var scheduler = new JobScheduler(queue, prompt -> {
            if (prompt.equals("first-fails")) {
                firstAttempted.countDown();
                throw new IllegalStateException("expected generation failure");
            }
            return "image:" + prompt;
        }, settings);
        try (scheduler) {
            scheduler.start();
            assertTrue(firstAttempted.await(5, TimeUnit.SECONDS));
            var later = queue.request("later-success");

            Job completed = awaitJob(later.jobId(), job -> job.status() == JobStatus.SUCCEEDED);

            assertEquals(later.jobId(), completed.jobId());
            assertEquals("image:later-success", queue.findMonster(later.monsterId()).orElseThrow().image());
            Job failed = awaitJob(first.jobId(), job -> job.status() == JobStatus.PENDING);
            assertEquals(1, failed.attemptCount());
            assertTrue(failed.lastError().contains("expected generation failure"));
        }
        assertTrue(scheduler.isTerminated());
    }

    @Test
    void Scheduler_경계로_전파된_조회_예외_이후에도_반복_Polling을_계속한다() throws Exception {
        var target = queue.request("after-query-error");
        db.dataSource.failNextCandidateQuery();
        var scheduler = new JobScheduler(queue, prompt -> "image:" + prompt, settings);
        try (scheduler) {
            scheduler.start();
            assertTrue(db.dataSource.candidateFailureObserved.await(5, TimeUnit.SECONDS));

            Job completed = awaitJob(target.jobId(), job -> job.status() == JobStatus.SUCCEEDED);

            assertEquals(1, completed.attemptCount());
            assertEquals("image:after-query-error", queue.findMonster(target.monsterId()).orElseThrow().image());
        }
        assertTrue(scheduler.isTerminated());
    }

    @Test
    void 실행_슬롯_수만큼만_선점하고_초과_작업은_DB에서_대기한다() throws Exception {
        List<JobQueue.Registration> registered = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            registered.add(queue.request("job-" + i));
        }
        var started = new CountDownLatch(settings.concurrency());
        var release = new CountDownLatch(1);
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        var scheduler = new JobScheduler(queue, prompt -> {
            maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test release timeout");
                }
                return "image:" + prompt;
            } finally {
                active.decrementAndGet();
            }
        }, settings);
        try (scheduler) {
            try {
                scheduler.start();
                assertTrue(started.await(5, TimeUnit.SECONDS));
                scheduler.dispatch();
                scheduler.dispatch();
                assertEquals(2, active.get());
                assertEquals(2, db.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM image_generation_job WHERE status = 'RUNNING'", Integer.class));
                for (int i = 2; i < registered.size(); i++) {
                    Job waiting = queue.findJob(registered.get(i).jobId()).orElseThrow();
                    assertEquals(JobStatus.PENDING, waiting.status());
                    assertEquals(0, waiting.attemptCount());
                }
            } finally {
                release.countDown();
            }
            for (var target : registered) {
                awaitJob(target.jobId(), job -> job.status() == JobStatus.SUCCEEDED);
                assertEquals("image:" + queue.findJob(target.jobId()).orElseThrow().prompt(),
                        queue.findMonster(target.monsterId()).orElseThrow().image());
            }
            assertEquals(settings.concurrency(), maximum.get());
        }
        assertTrue(scheduler.isTerminated());
    }

    @Test
    void Generator가_막혀도_기한_복구는_진행하고_실제_실행_슬롯은_반환하지_않는다() throws Exception {
        settings = new QueueSettings(settings.processingTimeout(), 3, settings.retryDelay(), 1,
                settings.pollingInterval(), settings.recoveryInterval());
        queue = new JobQueue(db.dataSource, clock, settings);
        var target = queue.request("blocked");
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var returned = new CountDownLatch(1);
        var scheduler = new JobScheduler(queue, prompt -> {
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test release timeout");
                }
                return "late-image";
            } finally {
                returned.countDown();
            }
        }, settings);
        try (scheduler) {
            try {
                scheduler.start();
                assertTrue(started.await(5, TimeUnit.SECONDS));
                clock.advance(settings.processingTimeout());

                Job recovered = awaitJob(target.jobId(), job -> job.status() == JobStatus.PENDING);

                assertEquals(1, recovered.attemptCount());
                assertNull(recovered.claimToken());
                assertEquals(1, returned.getCount(), "기한 만료는 Generator 실행을 중단하지 않는다.");
                var later = queue.request("waiting-for-slot");
                scheduler.dispatch();
                assertEquals(0, queue.findJob(later.jobId()).orElseThrow().attemptCount());
            } finally {
                release.countDown();
            }
        }
        assertTrue(scheduler.isTerminated());
        assertNull(queue.findMonster(target.monsterId()).orElseThrow().image(), "만료된 실행 결과는 반영하면 안 된다.");
    }

    @Test
    void 선점_후_제출_거부는_재시도로_넘기고_실행_슬롯을_반환한다() {
        var target = queue.request("rejected");
        var calls = new AtomicInteger();
        var executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1));
        executor.shutdown();
        var scheduler = new JobScheduler(queue, prompt -> {
            calls.incrementAndGet();
            return "unexpected";
        }, settings, executor);
        try (scheduler) {
            scheduler.dispatch();
            Job retry = queue.findJob(target.jobId()).orElseThrow();
            assertEquals(JobStatus.PENDING, retry.status());
            assertEquals(1, retry.attemptCount());
            assertNull(retry.claimToken());
            assertTrue(retry.lastError().contains("RejectedExecutionException"));

            clock.advance(settings.retryDelay());
            scheduler.dispatch();
            assertEquals(2, queue.findJob(target.jobId()).orElseThrow().attemptCount());
            assertEquals(0, calls.get());
        }
        assertTrue(scheduler.isTerminated());
    }

    @Test
    void 종료는_반복_호출할_수_있고_재시작은_거부한다() {
        var scheduler = new JobScheduler(queue, prompt -> "image", settings);
        scheduler.close();
        scheduler.close();

        assertTrue(scheduler.isTerminated());
        assertThrows(IllegalStateException.class, scheduler::start);
    }

    private Job awaitJob(long jobId, Predicate<Job> condition) throws InterruptedException {
        var observed = new CountDownLatch(1);
        var result = new AtomicReference<Job>();
        var error = new AtomicReference<RuntimeException>();
        var observer = Executors.newSingleThreadScheduledExecutor();
        try {
            observer.scheduleWithFixedDelay(() -> {
                try {
                    Job current = queue.findJob(jobId).orElseThrow();
                    if (condition.test(current)) {
                        result.set(current);
                        observed.countDown();
                    }
                } catch (RuntimeException failure) {
                    error.set(failure);
                    observed.countDown();
                }
            }, 0, 5, TimeUnit.MILLISECONDS);
            assertTrue(observed.await(5, TimeUnit.SECONDS), "job_id=" + jobId + "가 기대 상태에 도달하지 않았다.");
            if (error.get() != null) {
                throw new AssertionError("Job 상태 관찰 실패", error.get());
            }
            return result.get();
        } finally {
            observer.shutdownNow();
            assertTrue(observer.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
