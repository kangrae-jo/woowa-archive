package com.kng0501.dbpolling.web;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.cleanBaseline;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kng0501.dbpolling.application.DbPollingScheduler;
import com.kng0501.dbpolling.application.DbPollingWorker;
import com.kng0501.dbpolling.domain.ImageGenerator;
import com.kng0501.dbpolling.server.BaselineJobController;
import com.kng0501.technicalwriting.testsupport.BaselineWebIntegrationTest;
import com.kng0501.technicalwriting.testsupport.ComparisonJobFixtures;
import com.kng0501.dbqueue.server.HardenedJobController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@BaselineWebIntegrationTest
final class BaselineJobControllerTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ApplicationContext context;

    @Autowired
    BaselineJobControllerTest(
            final MockMvc mockMvc,
            final JdbcTemplate jdbc,
            final ApplicationContext context
    ) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.context = context;
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
    void web은_등록과_Monster_결과_조회만_제공하고_Worker_Bean은_만들지_않는다() throws Exception {
        mockMvc.perform(post("/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"" + ComparisonJobFixtures.PROMPT + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.monsterId").isNumber());

        final Long monsterId = jdbc.queryForObject("SELECT id FROM monster", Long.class);
        mockMvc.perform(get("/monsters/{monsterId}", monsterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monsterId").value(monsterId))
                .andExpect(jsonPath("$.prompt").value(ComparisonJobFixtures.PROMPT))
                .andExpect(jsonPath("$.image").value(nullValue()));

        assertTrue(context.getBeansOfType(BaselineJobController.class).size() == 1);
        assertTrue(context.getBeansOfType(HardenedJobController.class).isEmpty());
        assertTrue(context.getBeansOfType(DbPollingWorker.class).isEmpty());
        assertTrue(context.getBeansOfType(DbPollingScheduler.class).isEmpty());
        assertTrue(context.getBeansOfType(ImageGenerator.class).isEmpty());
    }
}
