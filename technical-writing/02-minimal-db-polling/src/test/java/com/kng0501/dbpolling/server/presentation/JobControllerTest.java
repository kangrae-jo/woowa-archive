package com.kng0501.dbpolling.server.presentation;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.clean02;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kng0501.dbpolling.server.ServerIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@ServerIntegrationTest
final class JobControllerTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ApplicationContext context;

    @Autowired
    JobControllerTest(
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
        clean02(jdbc);
    }

    @AfterEach
    void tearDown() {
        clean02(jdbc);
    }

    @Test
    void 서버는_등록과_Monster_결과_조회만_제공한다() throws Exception {
        mockMvc.perform(post("/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"comparison prompt\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.monsterId").isNumber());

        final Long monsterId = jdbc.queryForObject("SELECT id FROM monster", Long.class);
        mockMvc.perform(get("/monsters/{monsterId}", monsterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monsterId").value(monsterId))
                .andExpect(jsonPath("$.prompt").value("comparison prompt"))
                .andExpect(jsonPath("$.image").value(nullValue()));

        assertTrue(context.getBeansOfType(JobController.class).size() == 1);
        assertTrue(context.getBeansOfType(Object.class).values().stream()
                .map(Object::getClass)
                .noneMatch(type -> type.getName().startsWith("com.kng0501.dbpolling.worker.")));
    }
}
