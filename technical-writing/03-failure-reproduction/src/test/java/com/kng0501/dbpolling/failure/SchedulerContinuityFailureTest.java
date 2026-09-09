package com.kng0501.dbpolling.failure;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbpolling.application.DbPollingScheduler;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;
import com.kng0501.technicalwriting.testsupport.BaselineIntegrationTest;
import com.kng0501.technicalwriting.testsupport.BaselineJobRegistrationFixture;
import com.kng0501.technicalwriting.testsupport.BaselineTestImageGenerator;
import com.kng0501.technicalwriting.testsupport.MySqlTestDatabase;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@BaselineIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("failure-reproduction")
final class SchedulerContinuityFailureTest {

    private final ImageGenerationRequestRepository requestRepository;
    private final MonsterRepository monsterRepository;
    private final DbPollingScheduler scheduler;
    private final BaselineTestImageGenerator generator;
    private final JdbcTemplate jdbc;

    @Autowired
    SchedulerContinuityFailureTest(
            final ImageGenerationRequestRepository requestRepository,
            final MonsterRepository monsterRepository,
            final DbPollingScheduler scheduler,
            final BaselineTestImageGenerator generator,
            final JdbcTemplate jdbc
    ) {
        this.requestRepository = requestRepository;
        this.monsterRepository = monsterRepository;
        this.scheduler = scheduler;
        this.generator = generator;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        MySqlTestDatabase.cleanBaseline(jdbc);
        generator.reset();
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
        MySqlTestDatabase.cleanBaseline(jdbc);
        generator.reset();
    }

    @Test
    void 한_작업의_실패가_이후_polling을_중단하지_않는다() throws InterruptedException {
        final var firstAttempted = new CountDownLatch(1);
        final var laterJobProcessed = new CountDownLatch(1);
        final var invocationCount = new AtomicInteger();
        generator.use(prompt -> {
            if (invocationCount.incrementAndGet() == 1) {
                firstAttempted.countDown();
                throw new SimulatedGenerationFailureException();
            }
            laterJobProcessed.countDown();
            return "image:" + prompt;
        });

        BaselineJobRegistrationFixture.register(monsterRepository, requestRepository, "first request");
        scheduler.start();
        assertTrue(
                firstAttempted.await(2, TimeUnit.SECONDS),
                "첫 번째 작업이 제한 시간 안에 실행되지 않아 테스트를 준비할 수 없습니다."
        );

        BaselineJobRegistrationFixture.register(monsterRepository, requestRepository, "later request");
        final boolean processed = laterJobProcessed.await(1, TimeUnit.SECONDS);

        assertAll(
                () -> assertTrue(
                        processed,
                        "한 작업이 실패해도 Scheduler는 이후 Polling을 계속해야 합니다."
                ),
                () -> assertEquals(
                        0,
                        requestRepository.count(),
                        "이후 등록한 작업이 처리되지 않고 큐에 남았습니다."
                )
        );
    }

    private static final class SimulatedGenerationFailureException extends RuntimeException {
    }
}
