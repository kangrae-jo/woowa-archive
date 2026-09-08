package com.kng0501.dbpolling.failure;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kng0501.dbpolling.application.DbPollingWorker;
import com.kng0501.dbpolling.application.ImageGenerationService;
import com.kng0501.dbpolling.domain.ImageGenerationRequest;
import com.kng0501.dbpolling.domain.ImageGenerator;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;
import com.kng0501.technicalwriting.testsupport.BaselineIntegrationTest;
import com.kng0501.technicalwriting.testsupport.MySqlTestDatabase;
import java.util.Optional;
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

@BaselineIntegrationTest
@Tag("failure-reproduction")
final class DuplicateClaimFailureTest {

    private final MonsterRepository monsterRepository;
    private final ImageGenerationRequestRepository requestRepository;
    private final ImageGenerationService imageGenerationService;
    private final JdbcTemplate jdbc;

    @Autowired
    DuplicateClaimFailureTest(
            final MonsterRepository monsterRepository,
            final ImageGenerationRequestRepository requestRepository,
            final ImageGenerationService imageGenerationService,
            final JdbcTemplate jdbc
    ) {
        this.monsterRepository = monsterRepository;
        this.requestRepository = requestRepository;
        this.imageGenerationService = imageGenerationService;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        MySqlTestDatabase.cleanBaseline(jdbc);
    }

    @AfterEach
    void tearDown() {
        MySqlTestDatabase.cleanBaseline(jdbc);
    }

    @Test
    void 두_워커는_같은_작업을_한_번만_처리한다() throws Exception {
        imageGenerationService.request("blue dragon");
        final var synchronizedRepository = new BarrierRequestRepository(
                requestRepository,
                new CyclicBarrier(2)
        );
        final var generationCount = new AtomicInteger();
        final ImageGenerator generator = prompt -> {
            generationCount.incrementAndGet();
            return "image:" + prompt;
        };
        final var firstWorker = new DbPollingWorker(synchronizedRepository, monsterRepository, generator);
        final var secondWorker = new DbPollingWorker(synchronizedRepository, monsterRepository, generator);

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

    private static final class BarrierRequestRepository implements ImageGenerationRequestRepository {

        private final ImageGenerationRequestRepository delegate;
        private final CyclicBarrier barrier;

        private BarrierRequestRepository(
                final ImageGenerationRequestRepository delegate,
                final CyclicBarrier barrier
        ) {
            this.delegate = delegate;
            this.barrier = barrier;
        }

        @Override
        public long enqueue(final String prompt) {
            return delegate.enqueue(prompt);
        }

        @Override
        public Optional<ImageGenerationRequest> findOldest() {
            final Optional<ImageGenerationRequest> selected = delegate.findOldest();
            awaitSelectionOfBothWorkers();
            return selected;
        }

        @Override
        public void deleteById(final long requestId) {
            delegate.deleteById(requestId);
        }

        @Override
        public long count() {
            return delegate.count();
        }

        private void awaitSelectionOfBothWorkers() {
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
}
