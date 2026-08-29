package com.kng0501.dbpolling.failure;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbpolling.application.DbPollingScheduler;
import com.kng0501.dbpolling.application.DbPollingWorker;
import com.kng0501.dbpolling.application.ImageGenerationService;
import com.kng0501.dbpolling.domain.ImageGenerator;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.JdbcImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.JdbcMonsterRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;
import com.kng0501.dbpolling.support.TestDatabase;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("failure-reproduction")
class SchedulerContinuityFailureTest {

    private MonsterRepository monsterRepository;
    private ImageGenerationRequestRepository requestRepository;
    private ImageGenerationService imageGenerationService;

    @BeforeEach
    void setUp() {
        var dataSource = TestDatabase.createInitializedDataSource();
        monsterRepository = new JdbcMonsterRepository(dataSource);
        requestRepository = new JdbcImageGenerationRequestRepository(dataSource);
        imageGenerationService = new ImageGenerationService(monsterRepository, requestRepository);
    }

    @Test
    void 한_작업의_실패가_이후_polling을_중단하지_않는다() throws InterruptedException {
        var firstAttempted = new CountDownLatch(1);
        var laterJobProcessed = new CountDownLatch(1);
        var invocationCount = new AtomicInteger();
        ImageGenerator generator = prompt -> {
            if (invocationCount.incrementAndGet() == 1) {
                firstAttempted.countDown();
                throw new SimulatedGenerationFailureException();
            }
            laterJobProcessed.countDown();
            return "image:" + prompt;
        };
        var worker = new DbPollingWorker(requestRepository, monsterRepository, generator);

        imageGenerationService.request("first request");
        try (var scheduler = new DbPollingScheduler(worker, Duration.ofMillis(10))) {
            scheduler.start();
            assertTrue(
                    firstAttempted.await(1, TimeUnit.SECONDS),
                    "첫 번째 작업이 제한 시간 안에 실행되지 않아 테스트를 준비할 수 없습니다."
            );

            imageGenerationService.request("later request");
            boolean processed = laterJobProcessed.await(1, TimeUnit.SECONDS);

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
    }

    private static final class SimulatedGenerationFailureException extends RuntimeException {
    }
}
