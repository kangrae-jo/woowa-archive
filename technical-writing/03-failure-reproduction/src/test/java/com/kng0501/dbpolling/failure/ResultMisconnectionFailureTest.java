package com.kng0501.dbpolling.failure;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.clean02;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbpolling.worker.WorkerIntegrationTest;
import com.kng0501.dbpolling.worker.WorkerTestData;
import com.kng0501.dbpolling.worker.application.DbPollingWorker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@WorkerIntegrationTest
@Tag("failure-reproduction")
final class ResultMisconnectionFailureTest {

    private final DbPollingWorker worker;
    private final JdbcTemplate jdbc;

    @Autowired
    ResultMisconnectionFailureTest(final DbPollingWorker worker, final JdbcTemplate jdbc) {
        this.worker = worker;
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
    void 생성_결과는_요청한_monster에만_연결된다() {
        final long unrelatedMonsterId = WorkerTestData.saveMonster(jdbc, "unrelated");
        final long targetMonsterId = WorkerTestData.register(jdbc, "blue dragon");
        assertTrue(worker.pollOnce());

        final String unrelatedImage = imageOf(unrelatedMonsterId);
        final String targetImage = imageOf(targetMonsterId);
        assertAll(
                () -> assertFalse(
                        unrelatedImage != null,
                        "생성 결과가 요청과 무관한 monster에 연결됐습니다."
                ),
                () -> assertEquals(
                        "image:blue dragon",
                        targetImage,
                        "생성 결과가 요청한 monster에 연결되지 않았습니다."
                )
        );
    }

    private String imageOf(final long monsterId) {
        return jdbc.queryForObject("SELECT image FROM monster WHERE id = ?", String.class, monsterId);
    }
}
