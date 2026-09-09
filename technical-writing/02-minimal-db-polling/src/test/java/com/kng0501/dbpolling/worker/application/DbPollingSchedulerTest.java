package com.kng0501.dbpolling.worker.application;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.clean02;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.kng0501.dbpolling.worker.WorkerIntegrationTest;
import com.kng0501.dbpolling.worker.WorkerTestData;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@WorkerIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
final class DbPollingSchedulerTest {

    private final DbPollingScheduler scheduler;
    private final JdbcTemplate jdbc;

    @Autowired
    DbPollingSchedulerTest(
            final DbPollingScheduler scheduler,
            final JdbcTemplate jdbc
    ) {
        this.scheduler = scheduler;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        clean02(jdbc);
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
        clean02(jdbc);
    }

    @Test
    void scheduler가_주기적으로_요청을_polling한다() throws InterruptedException {
        final long monsterId = WorkerTestData.register(jdbc, "red turtle");

        scheduler.start();

        assertEquals("image:red turtle", awaitImage(monsterId, Duration.ofSeconds(2)));
    }

    private String awaitImage(final long monsterId, final Duration timeout) throws InterruptedException {
        final long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            final String image = jdbc.queryForObject("SELECT image FROM monster WHERE id = ?", String.class, monsterId);
            if (image != null) {
                return image;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return fail("제한 시간 안에 이미지 생성이 완료되지 않았습니다.");
    }
}
