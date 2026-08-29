package com.kng0501.dbpolling.failure;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kng0501.dbpolling.application.DbPollingWorker;
import com.kng0501.dbpolling.application.ImageGenerationService;
import com.kng0501.dbpolling.domain.ImageGenerationRequest;
import com.kng0501.dbpolling.domain.ImageGenerator;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.JdbcImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.JdbcMonsterRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;
import com.kng0501.dbpolling.support.TestDatabase;
import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("failure-reproduction")
class DuplicateClaimFailureTest {

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
    void 두_워커는_같은_작업을_한_번만_처리한다() throws Exception {
        imageGenerationService.request("blue dragon");
        var synchronizedRepository = new BarrierRequestRepository(
                requestRepository,
                new CyclicBarrier(2)
        );
        var generationCount = new AtomicInteger();
        ImageGenerator generator = prompt -> {
            generationCount.incrementAndGet();
            return "image:" + prompt;
        };
        var firstWorker = new DbPollingWorker(synchronizedRepository, monsterRepository, generator);
        var secondWorker = new DbPollingWorker(synchronizedRepository, monsterRepository, generator);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstResult = executor.submit(firstWorker::pollOnce);
            Future<Boolean> secondResult = executor.submit(secondWorker::pollOnce);

            int processedWorkerCount = countProcessedWorkers(
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

    private static int countProcessedWorkers(boolean firstResult, boolean secondResult) {
        return (firstResult ? 1 : 0) + (secondResult ? 1 : 0);
    }

    private static final class BarrierRequestRepository implements ImageGenerationRequestRepository {

        private final ImageGenerationRequestRepository delegate;
        private final CyclicBarrier barrier;

        private BarrierRequestRepository(
                ImageGenerationRequestRepository delegate,
                CyclicBarrier barrier
        ) {
            this.delegate = delegate;
            this.barrier = barrier;
        }

        @Override
        public long enqueue(String prompt) {
            return delegate.enqueue(prompt);
        }

        @Override
        public Optional<ImageGenerationRequest> findOldest() {
            Optional<ImageGenerationRequest> selected = delegate.findOldest();
            awaitSelectionOfBothWorkers();
            return selected;
        }

        @Override
        public void deleteById(long requestId) {
            delegate.deleteById(requestId);
        }

        @Override
        public long count() {
            return delegate.count();
        }

        private void awaitSelectionOfBothWorkers() {
            try {
                barrier.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("작업 선택 동기화 중 스레드가 중단됐습니다.", exception);
            } catch (BrokenBarrierException | TimeoutException exception) {
                throw new AssertionError("두 워커가 제한 시간 안에 작업을 선택하지 못했습니다.", exception);
            }
        }
    }
}
