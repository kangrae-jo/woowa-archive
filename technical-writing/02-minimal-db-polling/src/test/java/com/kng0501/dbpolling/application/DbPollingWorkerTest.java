package com.kng0501.dbpolling.application;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.cleanBaseline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbpolling.domain.Monster;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;
import com.kng0501.technicalwriting.testsupport.BaselineIntegrationTest;
import com.kng0501.technicalwriting.testsupport.BaselineJobRegistrationFixture;
import com.kng0501.technicalwriting.testsupport.BaselineTestImageGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@BaselineIntegrationTest
final class DbPollingWorkerTest {

    private final MonsterRepository monsterRepository;
    private final ImageGenerationRequestRepository requestRepository;
    private final DbPollingWorker worker;
    private final BaselineTestImageGenerator generator;
    private final JdbcTemplate jdbc;

    @Autowired
    DbPollingWorkerTest(
            final MonsterRepository monsterRepository,
            final ImageGenerationRequestRepository requestRepository,
            final DbPollingWorker worker,
            final BaselineTestImageGenerator generator,
            final JdbcTemplate jdbc
    ) {
        this.monsterRepository = monsterRepository;
        this.requestRepository = requestRepository;
        this.worker = worker;
        this.generator = generator;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        cleanBaseline(jdbc);
        generator.reset();
    }

    @AfterEach
    void tearDown() {
        cleanBaseline(jdbc);
        generator.reset();
    }

    @Test
    void 요청_한_건을_조회해_이미지를_생성한다() {
        final long monsterId = BaselineJobRegistrationFixture.register(
                monsterRepository, requestRepository, "blue dragon"
        );

        final boolean processed = worker.pollOnce();

        assertTrue(processed);
        assertEquals(0, requestRepository.count());
        assertEquals("image:blue dragon", findMonster(monsterId).image());
    }

    @Test
    void 처리할_요청이_없으면_false를_반환한다() {
        assertFalse(worker.pollOnce());
    }

    private Monster findMonster(final long monsterId) {
        return monsterRepository.findById(monsterId).orElseThrow();
    }
}
