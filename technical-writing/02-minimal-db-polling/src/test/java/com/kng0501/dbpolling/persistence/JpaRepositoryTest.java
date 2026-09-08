package com.kng0501.dbpolling.persistence;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.cleanBaseline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbpolling.domain.ImageGenerationRequest;
import com.kng0501.dbpolling.domain.Monster;
import com.kng0501.technicalwriting.testsupport.BaselineIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@BaselineIntegrationTest
final class JpaRepositoryTest {

    private final MonsterRepository monsterRepository;
    private final ImageGenerationRequestRepository requestRepository;
    private final JdbcTemplate jdbc;

    @Autowired
    JpaRepositoryTest(
            final MonsterRepository monsterRepository,
            final ImageGenerationRequestRepository requestRepository,
            final JdbcTemplate jdbc
    ) {
        this.monsterRepository = monsterRepository;
        this.requestRepository = requestRepository;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        cleanBaseline(jdbc);
    }

    @AfterEach
    void tearDown() {
        cleanBaseline(jdbc);
    }

    @Test
    void monster에_생성_결과를_저장한다() {
        final long monsterId = monsterRepository.save("blue dragon");

        final Monster saved = monsterRepository.findById(monsterId).orElseThrow();
        assertFalse(saved.hasImage());

        monsterRepository.updateImage(monsterId, "image:blue dragon");

        final Monster updated = monsterRepository.findById(monsterId).orElseThrow();
        assertTrue(updated.hasImage());
        assertEquals("image:blue dragon", updated.image());
    }

    @Test
    void 가장_오래된_요청부터_조회하고_삭제한다() {
        final long firstId = requestRepository.enqueue("first");
        requestRepository.enqueue("second");

        final ImageGenerationRequest oldest = requestRepository.findOldest().orElseThrow();
        assertEquals(firstId, oldest.id());
        assertEquals("first", oldest.prompt());

        requestRepository.deleteById(oldest.id());

        assertEquals(1, requestRepository.count());
        assertEquals("second", requestRepository.findOldest().orElseThrow().prompt());
    }
}
