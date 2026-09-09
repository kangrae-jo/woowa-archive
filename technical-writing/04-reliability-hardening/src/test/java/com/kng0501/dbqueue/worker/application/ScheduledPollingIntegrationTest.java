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
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@WorkerIntegrationTest
@TestPropertySource(properties = "db-queue.scheduling-enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
final class ScheduledPollingIntegrationTest {

    private final JdbcTemplate jdbc;
    private final TestClock clock;
    private final JobQueue queue;
    private final JobScheduler scheduler;
    private final TestImageGenerator generator;
    private final ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    ScheduledPollingIntegrationTest(
            final JdbcTemplate jdbc,
            final TestClock clock,
            final JobQueue queue,
            final JobScheduler scheduler,
            final TestImageGenerator generator,
            final ThreadPoolTaskScheduler taskScheduler
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.queue = queue;
        this.scheduler = scheduler;
        this.generator = generator;
        this.taskScheduler = taskScheduler;
    }

    @BeforeEach
    void setUp() {
        clean04(jdbc);
        clock.reset();
        generator.reset();
    }

    @AfterEach
    void tearDown() {
        taskScheduler.shutdown();
        scheduler.close();
        clean04(jdbc);
        clock.reset();
        generator.reset();
    }

    @Test
    void 한_작업의_실패_후에도_스케줄러가_특정_후속_Job을_완료한다() throws Exception {
        final CountDownLatch firstAttempted = new CountDownLatch(1);
        generator.use(prompt -> {
            if (prompt.equals("first-fails")) {
                firstAttempted.countDown();
                throw new IllegalStateException("expected scheduled failure");
            }
            return "image:" + prompt;
        });
        WorkerTestData.register(jdbc, "first-fails", clock.instant());
        assertTrue(firstAttempted.await(5, TimeUnit.SECONDS));

        final WorkerTestData.JobData later = WorkerTestData.register(jdbc, "later-success", clock.instant());
        final Job completed = awaitJob(later.jobId(), job -> job.status() == JobStatus.SUCCEEDED);

        assertEquals(later.jobId(), completed.jobId());
        assertEquals("image:later-success", imageOf(later.monsterId()));
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
            assertTrue(observed.await(5, TimeUnit.SECONDS), "후속 Job이 제한 시간 안에 완료되지 않았다.");
            return result.get();
        } finally {
            observer.shutdownNow();
            assertTrue(observer.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
