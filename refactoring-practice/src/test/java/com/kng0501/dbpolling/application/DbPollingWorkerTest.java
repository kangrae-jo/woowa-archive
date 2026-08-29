package com.kng0501.dbpolling.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.kng0501.dbpolling.domain.ImageGenerator;
import com.kng0501.dbpolling.domain.Monster;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.JdbcImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.JdbcMonsterRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;
import com.kng0501.dbpolling.support.TestDatabase;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DbPollingWorkerTest {

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
    void 요청_한_건을_조회해_이미지를_생성한다() {
        long monsterId = imageGenerationService.request("blue dragon");
        DbPollingWorker worker = createWorker();

        boolean processed = worker.pollOnce();

        assertTrue(processed);
        assertEquals(0, requestRepository.count());
        assertEquals("image:blue dragon", findMonster(monsterId).image());
    }

    @Test
    void 처리할_요청이_없으면_false를_반환한다() {
        DbPollingWorker worker = createWorker();

        assertFalse(worker.pollOnce());
    }

    @Test
    void scheduler가_주기적으로_요청을_polling한다() throws InterruptedException {
        long monsterId = imageGenerationService.request("red turtle");

        try (var scheduler = new DbPollingScheduler(createWorker(), Duration.ofMillis(10))) {
            scheduler.start();

            Monster processed = awaitProcessedMonster(monsterId, Duration.ofSeconds(1));
            assertEquals("image:red turtle", processed.image());
        }
    }

    private DbPollingWorker createWorker() {
        ImageGenerator generator = prompt -> "image:" + prompt;
        return new DbPollingWorker(requestRepository, monsterRepository, generator);
    }

    private Monster awaitProcessedMonster(long monsterId, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Monster monster = findMonster(monsterId);
            if (monster.hasImage()) {
                return monster;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return fail("제한 시간 안에 이미지 생성이 완료되지 않았습니다.");
    }

    private Monster findMonster(long monsterId) {
        return monsterRepository.findById(monsterId).orElseThrow();
    }
}
