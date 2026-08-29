package com.kng0501.dbpolling.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kng0501.dbpolling.application.DbPollingWorker;
import com.kng0501.dbpolling.application.ImageGenerationService;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.JdbcImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.JdbcMonsterRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;
import com.kng0501.dbpolling.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("failure-reproduction")
class WorkerTerminationFailureTest {

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
    void 워커가_종료되어도_처리중인_작업은_유실되지_않는다() {
        imageGenerationService.request("blue dragon");
        var stoppedWorker = new DbPollingWorker(
                requestRepository,
                monsterRepository,
                prompt -> {
                    throw new SimulatedWorkerStopException();
                }
        );

        assertThrows(SimulatedWorkerStopException.class, stoppedWorker::pollOnce);

        assertEquals(
                1,
                requestRepository.count(),
                "워커가 종료돼도 완료되지 않은 작업 행은 남아 있어야 합니다."
        );
    }

    private static final class SimulatedWorkerStopException extends RuntimeException {
    }
}
