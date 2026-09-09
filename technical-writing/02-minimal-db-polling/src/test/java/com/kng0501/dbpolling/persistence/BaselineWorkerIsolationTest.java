package com.kng0501.dbpolling.persistence;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.cleanBaseline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbpolling.application.DbPollingScheduler;
import com.kng0501.dbpolling.application.DbPollingWorker;
import com.kng0501.dbpolling.application.ImageGenerationService;
import com.kng0501.dbpolling.domain.ImageGenerator;
import com.kng0501.dbpolling.persistence.entity.BaselineImageGenerationRequestEntity;
import com.kng0501.dbpolling.persistence.entity.BaselineMonsterEntity;
import com.kng0501.dbpolling.persistence.jpa.BaselineMonsterJpaRepository;
import com.kng0501.dbpolling.server.BaselineJobController;
import com.kng0501.dbqueue.application.JobQueue;
import com.kng0501.dbqueue.persistence.jpa.ImageGenerationJobJpaRepository;
import com.kng0501.dbqueue.server.HardenedJobController;
import com.kng0501.technicalwriting.testsupport.BaselineIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

@BaselineIntegrationTest
final class BaselineWorkerIsolationTest {

    private final ApplicationContext context;
    private final EntityManagerFactory entityManagerFactory;
    private final JdbcTemplate jdbc;

    @Autowired
    BaselineWorkerIsolationTest(
            final ApplicationContext context,
            final EntityManagerFactory entityManagerFactory,
            final JdbcTemplate jdbc
    ) {
        this.context = context;
        this.entityManagerFactory = entityManagerFactory;
        this.jdbc = jdbc;
    }

    @AfterEach
    void tearDown() {
        cleanBaseline(jdbc);
    }

    @Test
    void baseline_워커는_02단계_작업_Bean과_영속성만_등록한다() {
        final Set<Class<?>> entities = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(type -> type.getJavaType())
                .collect(Collectors.toSet());

        assertFalse(context.getBeansOfType(BaselineMonsterJpaRepository.class).isEmpty());
        assertFalse(context.getBeansOfType(DbPollingWorker.class).isEmpty());
        assertFalse(context.getBeansOfType(DbPollingScheduler.class).isEmpty());
        assertFalse(context.getBeansOfType(ImageGenerator.class).isEmpty());
        assertTrue(context.getBeansOfType(ImageGenerationService.class).isEmpty());
        assertTrue(context.getBeansOfType(BaselineJobController.class).isEmpty());
        assertTrue(context.getBeansOfType(HardenedJobController.class).isEmpty());
        assertTrue(context.getBeansOfType(JobQueue.class).isEmpty());
        assertTrue(context.getBeansOfType(ImageGenerationJobJpaRepository.class).isEmpty());
        assertEquals(Set.of(BaselineMonsterEntity.class, BaselineImageGenerationRequestEntity.class), entities);
    }
}
