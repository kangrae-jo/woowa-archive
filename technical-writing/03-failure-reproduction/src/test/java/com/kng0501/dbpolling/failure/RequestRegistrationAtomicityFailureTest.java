package com.kng0501.dbpolling.failure;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kng0501.dbpolling.application.ImageGenerationService;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.technicalwriting.testsupport.BaselineIntegrationTest;
import com.kng0501.technicalwriting.testsupport.MySqlTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@BaselineIntegrationTest
@Tag("failure-reproduction")
final class RequestRegistrationAtomicityFailureTest {

    private final ImageGenerationService service;
    private final ImageGenerationRequestRepository requestRepository;
    private final JdbcTemplate jdbc;

    @Autowired
    RequestRegistrationAtomicityFailureTest(
            final ImageGenerationService service,
            final ImageGenerationRequestRepository requestRepository,
            final JdbcTemplate jdbc
    ) {
        this.service = service;
        this.requestRepository = requestRepository;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        MySqlTestDatabase.dropCheckIfExists(jdbc, "image_generation_request", "reject_baseline_enqueue");
        MySqlTestDatabase.cleanBaseline(jdbc);
    }

    @AfterEach
    void tearDown() {
        MySqlTestDatabase.dropCheckIfExists(jdbc, "image_generation_request", "reject_baseline_enqueue");
        MySqlTestDatabase.cleanBaseline(jdbc);
    }

    @Test
    void monster와_이미지_생성_job은_함께_저장되거나_함께_저장되지_않는다() {
        jdbc.execute("ALTER TABLE image_generation_request ADD CONSTRAINT reject_baseline_enqueue "
                + "CHECK (prompt <> 'enqueue-fail')");
        try {
            assertThrows(DataIntegrityViolationException.class, () -> service.request("enqueue-fail"));
        } finally {
            MySqlTestDatabase.dropCheckIfExists(jdbc, "image_generation_request", "reject_baseline_enqueue");
        }

        final Integer monsterCount = jdbc.queryForObject("SELECT COUNT(*) FROM monster", Integer.class);
        assertAll(
                () -> assertEquals(
                        0,
                        monsterCount,
                        "요청 등록이 실패하면 먼저 저장한 monster도 남지 않아야 합니다."
                ),
                () -> assertEquals(
                        0,
                        requestRepository.count(),
                        "요청 등록 실패 후 image generation job이 남지 않아야 합니다."
                )
        );
    }

}
