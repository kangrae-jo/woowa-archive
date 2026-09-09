package com.kng0501.dbqueue.web;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.cleanHardened;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kng0501.dbqueue.application.ExpiredJobRecovery;
import com.kng0501.dbqueue.application.ExpiredJobTransition;
import com.kng0501.dbqueue.application.JobPollingTasks;
import com.kng0501.dbqueue.application.JobScheduler;
import com.kng0501.dbqueue.application.JobWorker;
import com.kng0501.dbqueue.domain.ImageGenerator;
import com.kng0501.dbqueue.server.HardenedJobController;
import com.kng0501.dbpolling.server.BaselineJobController;
import com.kng0501.technicalwriting.testsupport.ComparisonJobFixtures;
import com.kng0501.technicalwriting.testsupport.HardenedWebIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@HardenedWebIntegrationTest
final class HardenedJobControllerTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ApplicationContext context;

    @Autowired
    HardenedJobControllerTest(
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
        cleanHardened(jdbc);
    }

    @AfterEach
    void tearDown() {
        cleanHardened(jdbc);
    }

    @Test
    void web은_등록과_상태_조회만_제공하고_Worker_실행_Bean은_만들지_않는다() throws Exception {
        mockMvc.perform(post("/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"" + ComparisonJobFixtures.PROMPT + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNumber())
                .andExpect(jsonPath("$.monsterId").isNumber());

        final Long jobId = jdbc.queryForObject("SELECT job_id FROM image_generation_job", Long.class);
        mockMvc.perform(get("/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.image").value(nullValue()));
        mockMvc.perform(get("/jobs/{jobId}", jobId + 1000))
                .andExpect(status().isNotFound());

        assertTrue(context.getBeansOfType(HardenedJobController.class).size() == 1);
        assertTrue(context.getBeansOfType(BaselineJobController.class).isEmpty());
        assertTrue(context.getBeansOfType(JobWorker.class).isEmpty());
        assertTrue(context.getBeansOfType(JobScheduler.class).isEmpty());
        assertTrue(context.getBeansOfType(JobPollingTasks.class).isEmpty());
        assertTrue(context.getBeansOfType(ExpiredJobRecovery.class).isEmpty());
        assertTrue(context.getBeansOfType(ExpiredJobTransition.class).isEmpty());
        assertTrue(context.getBeansOfType(ImageGenerator.class).isEmpty());
        assertTrue(!context.containsBean("jobExecutionExecutor"));
        assertTrue(!context.containsBean("taskScheduler"));
    }
}
