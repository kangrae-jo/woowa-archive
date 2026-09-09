package com.kng0501.dbqueue.server.application;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.clean04;
import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.count;
import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.dropCheckIfExists;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kng0501.dbqueue.server.ServerIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@ServerIntegrationTest
final class JobRegistrationServiceTest {

    private final JobRegistrationService registrationService;
    private final JdbcTemplate jdbc;

    @Autowired
    JobRegistrationServiceTest(final JobRegistrationService registrationService, final JdbcTemplate jdbc) {
        this.registrationService = registrationService;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        dropConstraints();
        clean04(jdbc);
    }

    @AfterEach
    void tearDown() {
        dropConstraints();
        clean04(jdbc);
    }

    @Test
    void 등록은_Monster와_PENDING_Job을_같은_트랜잭션으로_저장한다() {
        final JobRegistrationService.Registration registered = registrationService.request("dragon");

        assertEquals(1, count(jdbc, "queue_monster"));
        assertEquals(1, count(jdbc, "image_generation_job"));
        assertEquals(registered.monsterId(), jdbc.queryForObject(
                "SELECT monster_id FROM image_generation_job WHERE job_id = ?", Long.class, registered.jobId()
        ));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM image_generation_job WHERE job_id = ?", String.class, registered.jobId()
        ));
    }

    @Test
    void Job_저장이_실패하면_Monster도_롤백한다() {
        jdbc.execute("ALTER TABLE image_generation_job ADD CONSTRAINT reject_job "
                + "CHECK (prompt <> 'enqueue-fail')");

        assertThrows(DataIntegrityViolationException.class, () -> registrationService.request("enqueue-fail"));

        assertEquals(0, count(jdbc, "queue_monster"));
        assertEquals(0, count(jdbc, "image_generation_job"));
    }

    @Test
    void Monster_저장이_실패하면_Job도_저장하지_않는다() {
        jdbc.execute("ALTER TABLE queue_monster ADD CONSTRAINT reject_monster "
                + "CHECK (prompt <> 'save-fail')");

        assertThrows(DataIntegrityViolationException.class, () -> registrationService.request("save-fail"));

        assertEquals(0, count(jdbc, "queue_monster"));
        assertEquals(0, count(jdbc, "image_generation_job"));
    }

    private void dropConstraints() {
        dropCheckIfExists(jdbc, "image_generation_job", "reject_job");
        dropCheckIfExists(jdbc, "queue_monster", "reject_monster");
    }
}
