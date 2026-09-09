package com.kng0501.dbpolling.failure;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.clean02;
import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.dropCheckIfExists;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kng0501.dbpolling.server.ServerIntegrationTest;
import com.kng0501.dbpolling.server.application.ImageGenerationService;
import com.kng0501.dbpolling.server.persistence.jpa.ImageGenerationRequestJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@ServerIntegrationTest
@Tag("failure-reproduction")
final class RequestRegistrationAtomicityFailureTest {

    private final ImageGenerationService service;
    private final ImageGenerationRequestJpaRepository requests;
    private final JdbcTemplate jdbc;

    @Autowired
    RequestRegistrationAtomicityFailureTest(
            final ImageGenerationService service,
            final ImageGenerationRequestJpaRepository requests,
            final JdbcTemplate jdbc
    ) {
        this.service = service;
        this.requests = requests;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        dropCheckIfExists(jdbc, "image_generation_request", "reject_enqueue");
        clean02(jdbc);
    }

    @AfterEach
    void tearDown() {
        dropCheckIfExists(jdbc, "image_generation_request", "reject_enqueue");
        clean02(jdbc);
    }

    @Test
    void monster와_이미지_생성_job은_함께_저장되거나_함께_저장되지_않는다() {
        jdbc.execute("ALTER TABLE image_generation_request ADD CONSTRAINT reject_enqueue "
                + "CHECK (prompt <> 'enqueue-fail')");
        try {
            assertThrows(DataIntegrityViolationException.class, () -> service.request("enqueue-fail"));
        } finally {
            dropCheckIfExists(jdbc, "image_generation_request", "reject_enqueue");
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
                        requests.count(),
                        "요청 등록 실패 후 image generation job이 남지 않아야 합니다."
                )
        );
    }
}
