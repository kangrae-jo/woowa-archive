package com.kng0501.dbpolling.worker.application;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.clean02;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbpolling.worker.TestImageGenerator;
import com.kng0501.dbpolling.worker.WorkerIntegrationTest;
import com.kng0501.dbpolling.worker.WorkerTestData;
import com.kng0501.dbpolling.worker.persistence.jpa.ImageGenerationRequestJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@WorkerIntegrationTest
final class DbPollingWorkerTest {

    private final ImageGenerationRequestJpaRepository requests;
    private final DbPollingWorker worker;
    private final TestImageGenerator generator;
    private final JdbcTemplate jdbc;

    @Autowired
    DbPollingWorkerTest(
            final ImageGenerationRequestJpaRepository requests,
            final DbPollingWorker worker,
            final TestImageGenerator generator,
            final JdbcTemplate jdbc
    ) {
        this.requests = requests;
        this.worker = worker;
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
        clean02(jdbc);
        generator.reset();
    }

    @Test
    void 요청_한_건을_조회해_이미지를_생성한다() {
        final long monsterId = WorkerTestData.register(jdbc, "blue dragon");

        final boolean processed = worker.pollOnce();

        assertTrue(processed);
        assertEquals(0, requests.count());
        assertEquals("image:blue dragon", imageOf(monsterId));
    }

    @Test
    void 처리할_요청이_없으면_false를_반환한다() {
        assertFalse(worker.pollOnce());
    }

    private String imageOf(final long monsterId) {
        return jdbc.queryForObject("SELECT image FROM monster WHERE id = ?", String.class, monsterId);
    }
}
