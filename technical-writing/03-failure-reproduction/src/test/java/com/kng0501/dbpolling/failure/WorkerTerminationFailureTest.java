package com.kng0501.dbpolling.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kng0501.dbpolling.application.DbPollingWorker;
import com.kng0501.dbpolling.application.ImageGenerationService;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.technicalwriting.testsupport.BaselineIntegrationTest;
import com.kng0501.technicalwriting.testsupport.BaselineTestImageGenerator;
import com.kng0501.technicalwriting.testsupport.MySqlTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@BaselineIntegrationTest
@Tag("failure-reproduction")
final class WorkerTerminationFailureTest {

    private final ImageGenerationRequestRepository requestRepository;
    private final ImageGenerationService imageGenerationService;
    private final DbPollingWorker worker;
    private final BaselineTestImageGenerator generator;
    private final JdbcTemplate jdbc;

    @Autowired
    WorkerTerminationFailureTest(
            final ImageGenerationRequestRepository requestRepository,
            final ImageGenerationService imageGenerationService,
            final DbPollingWorker worker,
            final BaselineTestImageGenerator generator,
            final JdbcTemplate jdbc
    ) {
        this.requestRepository = requestRepository;
        this.imageGenerationService = imageGenerationService;
        this.worker = worker;
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
        MySqlTestDatabase.cleanBaseline(jdbc);
        generator.reset();
    }

    @Test
    void 워커가_종료되어도_처리중인_작업은_유실되지_않는다() {
        imageGenerationService.request("blue dragon");
        generator.use(prompt -> {
            throw new SimulatedWorkerStopException();
        });

        assertThrows(SimulatedWorkerStopException.class, worker::pollOnce);

        assertEquals(
                1,
                requestRepository.count(),
                "워커가 종료돼도 완료되지 않은 작업 행은 남아 있어야 합니다."
        );
    }

    private static final class SimulatedWorkerStopException extends RuntimeException {
    }
}
