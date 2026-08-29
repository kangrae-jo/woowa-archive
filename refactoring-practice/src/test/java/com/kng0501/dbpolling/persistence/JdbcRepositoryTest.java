package com.kng0501.dbpolling.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbpolling.domain.ImageGenerationRequest;
import com.kng0501.dbpolling.domain.Monster;
import com.kng0501.dbpolling.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcRepositoryTest {

    private MonsterRepository monsterRepository;
    private ImageGenerationRequestRepository requestRepository;

    @BeforeEach
    void setUp() {
        var dataSource = TestDatabase.createInitializedDataSource();
        monsterRepository = new JdbcMonsterRepository(dataSource);
        requestRepository = new JdbcImageGenerationRequestRepository(dataSource);
    }

    @Test
    void monster에_생성_결과를_저장한다() {
        long monsterId = monsterRepository.save("blue dragon");

        Monster saved = monsterRepository.findById(monsterId).orElseThrow();
        assertFalse(saved.hasImage());

        monsterRepository.updateImage(monsterId, "image:blue dragon");

        Monster updated = monsterRepository.findById(monsterId).orElseThrow();
        assertTrue(updated.hasImage());
        assertEquals("image:blue dragon", updated.image());
    }

    @Test
    void 가장_오래된_요청부터_조회하고_삭제한다() {
        long firstId = requestRepository.enqueue("first");
        requestRepository.enqueue("second");

        ImageGenerationRequest oldest = requestRepository.findOldest().orElseThrow();
        assertEquals(firstId, oldest.id());
        assertEquals("first", oldest.prompt());

        requestRepository.deleteById(oldest.id());

        assertEquals(1, requestRepository.count());
        assertEquals("second", requestRepository.findOldest().orElseThrow().prompt());
    }
}
