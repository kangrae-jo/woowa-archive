package com.kng0501.dbpolling.failure;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.clean02;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kng0501.dbpolling.worker.WorkerIntegrationTest;
import com.kng0501.dbpolling.worker.WorkerTestData;
import com.kng0501.dbpolling.worker.application.DbPollingWorker;
import com.kng0501.dbpolling.worker.domain.ImageGenerator;
import com.kng0501.dbpolling.worker.persistence.ImageResultUpdater;
import com.kng0501.dbpolling.worker.persistence.jpa.ImageGenerationRequestJpaRepository;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@WorkerIntegrationTest
@Tag("failure-reproduction")
final class DuplicateClaimFailureTest {

    private final ImageGenerationRequestJpaRepository requests;
    private final ImageResultUpdater results;
    private final JdbcTemplate jdbc;

    @Autowired
    DuplicateClaimFailureTest(
            final ImageGenerationRequestJpaRepository requests,
            final ImageResultUpdater results,
            final JdbcTemplate jdbc
    ) {
        this.requests = requests;
        this.results = results;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        clean02(jdbc);
    }

    @AfterEach
    void tearDown() {
        clean02(jdbc);
    }

    @Test
    void 두_워커는_같은_작업을_한_번만_처리한다() throws Exception {
        WorkerTestData.register(jdbc, "blue dragon");
        final CyclicBarrier selectedByBothWorkers = new CyclicBarrier(2);
        final AtomicInteger generationCount = new AtomicInteger();
        final ImageGenerator generator = prompt -> {
            generationCount.incrementAndGet();
            return "image:" + prompt;
        };
        final Runnable afterSelection = () -> awaitSelectionOfBothWorkers(selectedByBothWorkers);
        final DbPollingWorker firstWorker = new DbPollingWorker(requests, results, generator, afterSelection);
        final DbPollingWorker secondWorker = new DbPollingWorker(requests, results, generator, afterSelection);

        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<Boolean> firstResult = executor.submit(firstWorker::pollOnce);
            final Future<Boolean> secondResult = executor.submit(secondWorker::pollOnce);

            final int processedWorkerCount = countProcessedWorkers(
                    firstResult.get(2, TimeUnit.SECONDS),
                    secondResult.get(2, TimeUnit.SECONDS)
            );
            assertAll(
                    () -> assertEquals(
                            1,
                            processedWorkerCount,
                            "작업 한 건은 한 워커만 처리해야 합니다."
                    ),
                    () -> assertEquals(
                            1,
                            generationCount.get(),
                            "작업 한 건에서 이미지 생성은 한 번만 호출되어야 합니다."
                    )
            );
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                throw new AssertionError("테스트 Executor가 제한 시간 안에 종료되지 않았습니다.");
            }
        }
    }

    private static int countProcessedWorkers(final boolean firstResult, final boolean secondResult) {
        return (firstResult ? 1 : 0) + (secondResult ? 1 : 0);
    }

    private static void awaitSelectionOfBothWorkers(final CyclicBarrier barrier) {
        try {
            barrier.await(1, TimeUnit.SECONDS);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("작업 선택 동기화 중 스레드가 중단됐습니다.", exception);
        } catch (final BrokenBarrierException | TimeoutException exception) {
            throw new AssertionError("두 워커가 제한 시간 안에 작업을 선택하지 못했습니다.", exception);
        }
    }
}
