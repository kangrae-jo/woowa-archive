package com.kng0501.dbpolling.failure;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.clean02;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbpolling.worker.TestImageGenerator;
import com.kng0501.dbpolling.worker.WorkerIntegrationTest;
import com.kng0501.dbpolling.worker.WorkerTestData;
import com.kng0501.dbpolling.worker.application.DbPollingScheduler;
import com.kng0501.dbpolling.worker.persistence.jpa.ImageGenerationRequestJpaRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@WorkerIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("failure-reproduction")
final class SchedulerContinuityFailureTest {

    private final ImageGenerationRequestJpaRepository requests;
    private final DbPollingScheduler scheduler;
    private final TestImageGenerator generator;
    private final JdbcTemplate jdbc;

    @Autowired
    SchedulerContinuityFailureTest(
            final ImageGenerationRequestJpaRepository requests,
            final DbPollingScheduler scheduler,
            final TestImageGenerator generator,
            final JdbcTemplate jdbc
    ) {
        this.requests = requests;
        this.scheduler = scheduler;
        this.generator = generator;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        clean02(jdbc);
        generator.reset();
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
        clean02(jdbc);
        generator.reset();
    }

    @Test
    void 한_작업의_실패가_이후_polling을_중단하지_않는다() throws InterruptedException {
        final CountDownLatch firstAttempted = new CountDownLatch(1);
        final CountDownLatch laterJobProcessed = new CountDownLatch(1);
        final AtomicInteger invocationCount = new AtomicInteger();
        generator.use(prompt -> {
            if (invocationCount.incrementAndGet() == 1) {
                firstAttempted.countDown();
                throw new SimulatedGenerationFailureException();
            }
            laterJobProcessed.countDown();
            return "image:" + prompt;
        });

        WorkerTestData.register(jdbc, "first request");
        scheduler.start();
        assertTrue(
                firstAttempted.await(2, TimeUnit.SECONDS),
                "첫 번째 작업이 제한 시간 안에 실행되지 않아 테스트를 준비할 수 없습니다."
        );

        WorkerTestData.register(jdbc, "later request");
        final boolean processed = laterJobProcessed.await(1, TimeUnit.SECONDS);

        assertAll(
                () -> assertTrue(
                        processed,
                        "한 작업이 실패해도 Scheduler는 이후 Polling을 계속해야 합니다."
                ),
                () -> assertEquals(
                        0,
                        requests.count(),
                        "이후 등록한 작업이 처리되지 않고 큐에 남았습니다."
                )
        );
    }

    private static final class SimulatedGenerationFailureException extends RuntimeException {
    }
}
