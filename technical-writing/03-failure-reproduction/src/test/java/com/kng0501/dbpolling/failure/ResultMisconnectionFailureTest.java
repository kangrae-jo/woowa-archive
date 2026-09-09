package com.kng0501.dbpolling.failure;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbpolling.application.DbPollingWorker;
import com.kng0501.dbpolling.domain.Monster;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;
import com.kng0501.technicalwriting.testsupport.BaselineIntegrationTest;
import com.kng0501.technicalwriting.testsupport.BaselineJobRegistrationFixture;
import com.kng0501.technicalwriting.testsupport.MySqlTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@BaselineIntegrationTest
@Tag("failure-reproduction")
final class ResultMisconnectionFailureTest {

    private final MonsterRepository monsterRepository;
    private final ImageGenerationRequestRepository requestRepository;
    private final DbPollingWorker worker;
    private final JdbcTemplate jdbc;

    @Autowired
    ResultMisconnectionFailureTest(
            final MonsterRepository monsterRepository,
            final ImageGenerationRequestRepository requestRepository,
            final DbPollingWorker worker,
            final JdbcTemplate jdbc
    ) {
        this.monsterRepository = monsterRepository;
        this.requestRepository = requestRepository;
        this.worker = worker;
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
    void 생성_결과는_요청한_monster에만_연결된다() {
        final long unrelatedMonsterId = monsterRepository.save("unrelated");
        final long targetMonsterId = BaselineJobRegistrationFixture.register(
                monsterRepository, requestRepository, "blue dragon"
        );
        assertTrue(worker.pollOnce());

        final Monster unrelatedMonster = monsterRepository.findById(unrelatedMonsterId).orElseThrow();
        final Monster targetMonster = monsterRepository.findById(targetMonsterId).orElseThrow();
        assertAll(
                () -> assertFalse(
                        unrelatedMonster.hasImage(),
                        "생성 결과가 요청과 무관한 monster에 연결됐습니다."
                ),
                () -> assertEquals(
                        "image:blue dragon",
                        targetMonster.image(),
                        "생성 결과가 요청한 monster에 연결되지 않았습니다."
                )
        );
    }
}
