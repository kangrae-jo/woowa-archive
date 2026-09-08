package com.kng0501.dbqueue.application;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.cleanHardened;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.JobStatus;
import com.kng0501.technicalwriting.testsupport.HardenedIntegrationTest;
import com.kng0501.technicalwriting.testsupport.HardenedTestImageGenerator;
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

@HardenedIntegrationTest
@TestPropertySource(properties = "db-queue.scheduling-enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
final class ScheduledPollingIntegrationTest {

    private final JdbcTemplate jdbc;
    private final JobQueue queue;
    private final JobScheduler scheduler;
    private final HardenedTestImageGenerator generator;
    private final ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    ScheduledPollingIntegrationTest(
            final JdbcTemplate jdbc,
            final JobQueue queue,
            final JobScheduler scheduler,
            final HardenedTestImageGenerator generator,
            final ThreadPoolTaskScheduler taskScheduler
    ) {
        this.jdbc = jdbc;
        this.queue = queue;
        this.scheduler = scheduler;
        this.generator = generator;
        this.taskScheduler = taskScheduler;
    }

    @BeforeEach
    void setUp() {
        cleanHardened(jdbc);
        generator.reset();
    }

    @AfterEach
    void tearDown() {
        taskScheduler.shutdown();
        scheduler.close();
        cleanHardened(jdbc);
        generator.reset();
    }

    @Test
    void 한_작업의_실패_후에도_Spring_Scheduler가_특정_후속_Job을_완료한다() throws Exception {
        final var firstAttempted = new CountDownLatch(1);
        generator.use(prompt -> {
            if (prompt.equals("first-fails")) {
                firstAttempted.countDown();
                throw new IllegalStateException("expected scheduled failure");
            }
            return "image:" + prompt;
        });
        queue.request("first-fails");
        assertTrue(firstAttempted.await(5, TimeUnit.SECONDS));

        final JobQueue.Registration later = queue.request("later-success");
        final Job completed = awaitJob(later.jobId(), job -> job.status() == JobStatus.SUCCEEDED);

        assertEquals(later.jobId(), completed.jobId());
        assertEquals("image:later-success", queue.findMonster(later.monsterId()).orElseThrow().image());
    }

    private Job awaitJob(final long jobId, final Predicate<Job> condition) throws InterruptedException {
        final var observed = new CountDownLatch(1);
        final var result = new AtomicReference<Job>();
        final var observer = Executors.newSingleThreadScheduledExecutor();
        try {
            observer.scheduleWithFixedDelay(() -> {
                final Job current = queue.findJob(jobId).orElseThrow();
                if (condition.test(current)) {
                    result.set(current);
                    observed.countDown();
                }
            }, 0, 5, TimeUnit.MILLISECONDS);
            assertTrue(observed.await(5, TimeUnit.SECONDS), "후속 Job이 제한 시간 안에 완료되지 않았다.");
            return result.get();
        } finally {
            observer.shutdownNow();
            assertTrue(observer.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
