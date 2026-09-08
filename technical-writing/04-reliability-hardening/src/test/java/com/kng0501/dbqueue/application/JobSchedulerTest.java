package com.kng0501.dbqueue.application;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.cleanHardened;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.JobStatus;
import com.kng0501.dbqueue.domain.QueueSettings;
import com.kng0501.dbqueue.support.MutableClock;
import com.kng0501.technicalwriting.testsupport.HardenedIntegrationTest;
import com.kng0501.technicalwriting.testsupport.HardenedTestImageGenerator;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@HardenedIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
final class JobSchedulerTest {

    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final QueueSettings settings;
    private final JobQueue queue;
    private final JobScheduler scheduler;
    private final HardenedTestImageGenerator generator;
    private final ThreadPoolExecutor executionPool;

    @Autowired
    JobSchedulerTest(
            final JdbcTemplate jdbc,
            final MutableClock clock,
            final QueueSettings settings,
            final JobQueue queue,
            final JobScheduler scheduler,
            final HardenedTestImageGenerator generator,
            @Qualifier("jobExecutionExecutor") final ThreadPoolExecutor executionPool
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
        this.queue = queue;
        this.scheduler = scheduler;
        this.generator = generator;
        this.executionPool = executionPool;
    }

    @BeforeEach
    void setUp() {
        cleanHardened(jdbc);
        clock.reset();
        generator.reset();
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
        cleanHardened(jdbc);
        clock.reset();
        generator.reset();
    }

    @Test
    void 첫_작업_실패_이후_등록한_특정_Job의_완료와_이미지를_확인한다() throws Exception {
        final JobQueue.Registration first = queue.request("first-fails");
        final var firstAttempted = new CountDownLatch(1);
        generator.use(prompt -> {
            if (prompt.equals("first-fails")) {
                firstAttempted.countDown();
                throw new IllegalStateException("expected generation failure");
            }
            return "image:" + prompt;
        });

        scheduler.dispatch();
        assertTrue(firstAttempted.await(5, TimeUnit.SECONDS));
        awaitJob(first.jobId(), job -> job.status() == JobStatus.PENDING);
        final JobQueue.Registration later = queue.request("later-success");
        scheduler.dispatch();

        final Job completed = awaitJob(later.jobId(), job -> job.status() == JobStatus.SUCCEEDED);
        assertEquals(later.jobId(), completed.jobId());
        assertEquals("image:later-success", queue.findMonster(later.monsterId()).orElseThrow().image());
        final Job failed = queue.findJob(first.jobId()).orElseThrow();
        assertEquals(1, failed.attemptCount());
        assertTrue(failed.lastError().contains("expected generation failure"));
    }

    @Test
    void Scheduler_경계의_예외를_격리한_뒤_후속_Job을_처리한다() throws Exception {
        final JobQueue.Registration target = queue.request("after-boundary-error");
        final JobScheduler failingOnce = mock(JobScheduler.class);
        doThrow(new IllegalStateException("injected dispatch failure"))
                .doAnswer(invocation -> {
                    scheduler.dispatch();
                    return null;
                })
                .when(failingOnce).dispatch();
        final var tasks = new JobPollingTasks(queue, failingOnce);

        assertDoesNotThrow(tasks::dispatch);
        tasks.dispatch();

        final Job completed = awaitJob(target.jobId(), job -> job.status() == JobStatus.SUCCEEDED);
        assertEquals(1, completed.attemptCount());
        assertEquals("image:after-boundary-error", queue.findMonster(target.monsterId()).orElseThrow().image());
    }

    @Test
    void 실행_슬롯_수만큼만_선점하고_초과_작업은_DB에서_대기한다() throws Exception {
        final List<JobQueue.Registration> registered = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            registered.add(queue.request("job-" + index));
        }
        final var started = new CountDownLatch(settings.concurrency());
        final var release = new CountDownLatch(1);
        final var active = new AtomicInteger();
        final var maximum = new AtomicInteger();
        generator.use(prompt -> {
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
        });

        try {
            scheduler.dispatch();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            scheduler.dispatch();
            assertEquals(2, active.get());
            assertEquals(2, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM image_generation_job WHERE status = 'RUNNING'", Integer.class
            ));
            for (int index = 2; index < registered.size(); index++) {
                final Job waiting = queue.findJob(registered.get(index).jobId()).orElseThrow();
                assertEquals(JobStatus.PENDING, waiting.status());
                assertEquals(0, waiting.attemptCount());
            }
        } finally {
            release.countDown();
        }

        awaitJob(registered.get(0).jobId(), job -> job.status() == JobStatus.SUCCEEDED);
        awaitJob(registered.get(1).jobId(), job -> job.status() == JobStatus.SUCCEEDED);
        scheduler.dispatch();
        for (final JobQueue.Registration target : registered) {
            awaitJob(target.jobId(), job -> job.status() == JobStatus.SUCCEEDED);
            assertEquals(
                    "image:" + queue.findJob(target.jobId()).orElseThrow().prompt(),
                    queue.findMonster(target.monsterId()).orElseThrow().image()
            );
        }
        assertEquals(settings.concurrency(), maximum.get());
    }

    @Test
    void Generator가_막혀도_기한_복구는_진행하고_실제_실행_슬롯은_반환하지_않는다() throws Exception {
        final JobQueue.Registration target = queue.request("blocked");
        final var started = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var returned = new CountDownLatch(1);
        generator.use(prompt -> {
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test release timeout");
                }
                return "late-image";
            } finally {
                returned.countDown();
            }
        });

        try {
            scheduler.dispatch();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            clock.advance(settings.processingTimeout());
            assertEquals(1, queue.recoverExpired());

            final Job recovered = queue.findJob(target.jobId()).orElseThrow();
            assertEquals(JobStatus.PENDING, recovered.status());
            assertEquals(1, recovered.attemptCount());
            assertNull(recovered.claimToken());
            assertEquals(1, returned.getCount(), "기한 만료는 Generator 실행을 중단하지 않는다.");
            final JobQueue.Registration later = queue.request("waiting-for-slot");
            scheduler.dispatch();
            assertEquals(0, queue.findJob(later.jobId()).orElseThrow().attemptCount());
        } finally {
            release.countDown();
        }

        assertTrue(returned.await(5, TimeUnit.SECONDS));
        assertNull(queue.findMonster(target.monsterId()).orElseThrow().image(), "만료된 실행 결과는 반영하면 안 된다.");
    }

    @Test
    void 선점_후_제출_거부는_재시도로_넘기고_실행_슬롯을_반환한다() {
        final JobQueue.Registration target = queue.request("rejected");
        final var calls = new AtomicInteger();
        generator.use(prompt -> {
            calls.incrementAndGet();
            return "unexpected";
        });
        executionPool.shutdown();

        scheduler.dispatch();
        final Job retry = queue.findJob(target.jobId()).orElseThrow();
        assertEquals(JobStatus.PENDING, retry.status());
        assertEquals(1, retry.attemptCount());
        assertNull(retry.claimToken());
        assertTrue(retry.lastError().contains("RejectedExecutionException"));

        clock.advance(settings.retryDelay());
        scheduler.dispatch();
        assertEquals(2, queue.findJob(target.jobId()).orElseThrow().attemptCount());
        assertEquals(0, calls.get());
    }

    @Test
    void 종료는_반복_호출할_수_있고_이후_dispatch는_작업을_선점하지_않는다() {
        final JobQueue.Registration target = queue.request("waiting");

        scheduler.close();
        scheduler.close();
        scheduler.dispatch();

        assertTrue(scheduler.isTerminated());
        assertEquals(JobStatus.PENDING, queue.findJob(target.jobId()).orElseThrow().status());
        assertEquals(0, queue.findJob(target.jobId()).orElseThrow().attemptCount());
    }

    private Job awaitJob(final long jobId, final Predicate<Job> condition) throws InterruptedException {
        final var observed = new CountDownLatch(1);
        final var result = new AtomicReference<Job>();
        final var error = new AtomicReference<RuntimeException>();
        final var observer = Executors.newSingleThreadScheduledExecutor();
        try {
            observer.scheduleWithFixedDelay(() -> {
                try {
                    final Job current = queue.findJob(jobId).orElseThrow();
                    if (condition.test(current)) {
                        result.set(current);
                        observed.countDown();
                    }
                } catch (final RuntimeException failure) {
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
