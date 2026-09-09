package com.kng0501.dbpolling.server.persistence;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.clean02;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.kng0501.dbpolling.server.ServerIntegrationTest;
import com.kng0501.dbpolling.server.application.ImageGenerationService;
import com.kng0501.dbpolling.server.persistence.jpa.ImageGenerationRequestJpaRepository;
import com.kng0501.dbpolling.server.persistence.jpa.MonsterJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@ServerIntegrationTest
final class JpaRepositoryTest {

    private final ImageGenerationService imageGenerationService;
    private final MonsterJpaRepository monsters;
    private final ImageGenerationRequestJpaRepository requests;
    private final JdbcTemplate jdbc;

    @Autowired
    JpaRepositoryTest(
            final ImageGenerationService imageGenerationService,
            final MonsterJpaRepository monsters,
            final ImageGenerationRequestJpaRepository requests,
            final JdbcTemplate jdbc
    ) {
        this.imageGenerationService = imageGenerationService;
        this.monsters = monsters;
        this.requests = requests;
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
    void 서버는_Monster와_이미지_생성_요청을_저장한다() {
        final long monsterId = imageGenerationService.request("blue dragon");

        assertEquals(1, monsters.count());
        assertEquals(1, requests.count());
        assertEquals("blue dragon", monsters.findById(monsterId).orElseThrow().getPrompt());
        assertFalse(imageGenerationService.findMonster(monsterId).orElseThrow().hasImage());
    }
}
