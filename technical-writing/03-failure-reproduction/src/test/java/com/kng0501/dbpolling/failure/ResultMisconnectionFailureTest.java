package com.kng0501.dbpolling.failure;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbpolling.application.DbPollingWorker;
import com.kng0501.dbpolling.application.ImageGenerationService;
import com.kng0501.dbpolling.domain.Monster;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.JdbcImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.JdbcMonsterRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;
import com.kng0501.dbpolling.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("failure-reproduction")
class ResultMisconnectionFailureTest {

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
    void 생성_결과는_요청한_monster에만_연결된다() {
        long unrelatedMonsterId = monsterRepository.save("unrelated");
        long targetMonsterId = imageGenerationService.request("blue dragon");
        var worker = new DbPollingWorker(
                requestRepository,
                monsterRepository,
                prompt -> "image:" + prompt
        );

        assertTrue(worker.pollOnce());

        Monster unrelatedMonster = monsterRepository.findById(unrelatedMonsterId).orElseThrow();
        Monster targetMonster = monsterRepository.findById(targetMonsterId).orElseThrow();
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
