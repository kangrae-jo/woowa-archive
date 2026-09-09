package com.kng0501.dbpolling.application;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.cleanBaseline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.kng0501.dbpolling.domain.Monster;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;
import com.kng0501.technicalwriting.testsupport.BaselineIntegrationTest;
import com.kng0501.technicalwriting.testsupport.BaselineJobRegistrationFixture;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@BaselineIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
final class DbPollingSchedulerTest {

    private final MonsterRepository monsterRepository;
    private final ImageGenerationRequestRepository requestRepository;
    private final DbPollingScheduler scheduler;
    private final JdbcTemplate jdbc;

    @Autowired
    DbPollingSchedulerTest(
            final MonsterRepository monsterRepository,
            final ImageGenerationRequestRepository requestRepository,
            final DbPollingScheduler scheduler,
            final JdbcTemplate jdbc
    ) {
        this.monsterRepository = monsterRepository;
        this.requestRepository = requestRepository;
        this.scheduler = scheduler;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        cleanBaseline(jdbc);
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
        cleanBaseline(jdbc);
    }

    @Test
    void scheduler가_주기적으로_요청을_polling한다() throws InterruptedException {
        final long monsterId = BaselineJobRegistrationFixture.register(
                monsterRepository, requestRepository, "red turtle"
        );

        scheduler.start();

        final Monster processed = awaitProcessedMonster(monsterId, Duration.ofSeconds(2));
        assertEquals("image:red turtle", processed.image());
    }

    private Monster awaitProcessedMonster(final long monsterId, final Duration timeout) throws InterruptedException {
        final long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            final Monster monster = monsterRepository.findById(monsterId).orElseThrow();
            if (monster.hasImage()) {
                return monster;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return fail("제한 시간 안에 이미지 생성이 완료되지 않았습니다.");
    }
}
