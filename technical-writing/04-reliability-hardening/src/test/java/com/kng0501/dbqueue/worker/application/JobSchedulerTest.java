package com.kng0501.dbqueue.worker.application;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.clean04;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbqueue.worker.TestClock;
import com.kng0501.dbqueue.worker.TestImageGenerator;
import com.kng0501.dbqueue.worker.WorkerIntegrationTest;
import com.kng0501.dbqueue.worker.WorkerTestData;
import com.kng0501.dbqueue.worker.domain.Job;
import com.kng0501.dbqueue.worker.domain.JobStatus;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@WorkerIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
final class JobSchedulerTest {

    private final JdbcTemplate jdbc;
    private final TestClock clock;
    private final JobQueue queue;
    private final JobScheduler scheduler;
    private final TestImageGenerator generator;

    @Autowired
    JobSchedulerTest(
            final JdbcTemplate jdbc,
            final TestClock clock,
            final JobQueue queue,
            final JobScheduler scheduler,
            final TestImageGenerator generator
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.queue = queue;
        this.scheduler = scheduler;
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
        scheduler.close();
        clean04(jdbc);
        clock.reset();
        generator.reset();
    }

    @Test
    void 첫_작업_실패_후에도_후속_Job을_완료한다() throws Exception {
        final CountDownLatch firstAttempted = new CountDownLatch(1);
        generator.use(prompt -> {
            if (prompt.equals("first-fails")) {
                firstAttempted.countDown();
                throw new IllegalStateException("expected generation failure");
            }
            return "image:" + prompt;
        });
        final WorkerTestData.JobData first = register("first-fails");
        scheduler.dispatch();
        assertTrue(firstAttempted.await(5, TimeUnit.SECONDS));
        awaitJob(first.jobId(), job -> job.status() == JobStatus.PENDING);

        final WorkerTestData.JobData later = register("later-success");
        scheduler.dispatch();

        final Job completed = awaitJob(later.jobId(), job -> job.status() == JobStatus.SUCCEEDED);
        assertEquals(later.jobId(), completed.jobId());
        assertEquals("image:later-success", imageOf(later.monsterId()));
    }

    @Test
    void 종료_후_dispatch는_작업을_선점하지_않는다() {
        final WorkerTestData.JobData target = register("waiting");

        scheduler.close();
        scheduler.close();
        scheduler.dispatch();

        assertTrue(scheduler.isTerminated());
        assertEquals(JobStatus.PENDING, queue.findJob(target.jobId()).orElseThrow().status());
        assertEquals(0, queue.findJob(target.jobId()).orElseThrow().attemptCount());
    }

    private WorkerTestData.JobData register(final String prompt) {
        return WorkerTestData.register(jdbc, prompt, clock.instant());
    }

    private String imageOf(final long monsterId) {
        return jdbc.queryForObject("SELECT image FROM queue_monster WHERE monster_id = ?", String.class, monsterId);
    }

    private Job awaitJob(final long jobId, final Predicate<Job> condition) throws InterruptedException {
        final CountDownLatch observed = new CountDownLatch(1);
        final AtomicReference<Job> result = new AtomicReference<>();
        final var observer = Executors.newSingleThreadScheduledExecutor();
        try {
            observer.scheduleWithFixedDelay(() -> queue.findJob(jobId).ifPresent(job -> {
                if (condition.test(job)) {
                    result.set(job);
                    observed.countDown();
                }
            }), 0, 5, TimeUnit.MILLISECONDS);
            assertTrue(observed.await(5, TimeUnit.SECONDS), "job_id=" + jobId + "가 기대 상태에 도달하지 않았다.");
            return result.get();
        } finally {
            observer.shutdownNow();
            assertTrue(observer.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
