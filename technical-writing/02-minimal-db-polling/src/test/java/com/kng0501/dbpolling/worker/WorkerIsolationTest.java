package com.kng0501.dbpolling.worker;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.clean02;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbpolling.worker.application.DbPollingScheduler;
import com.kng0501.dbpolling.worker.application.DbPollingWorker;
import com.kng0501.dbpolling.worker.domain.ImageGenerator;
import com.kng0501.dbpolling.worker.persistence.entity.ImageGenerationRequestEntity;
import jakarta.persistence.EntityManagerFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

@WorkerIntegrationTest
final class WorkerIsolationTest {

    private final ApplicationContext context;
    private final EntityManagerFactory entityManagerFactory;
    private final JdbcTemplate jdbc;

    @Autowired
    WorkerIsolationTest(
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
        clean02(jdbc);
    }

    @Test
    void 워커는_자신의_작업_Bean과_영속성만_등록한다() {
        final Set<Class<?>> entities = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(type -> type.getJavaType())
                .collect(Collectors.toSet());

        assertFalse(context.getBeansOfType(DbPollingWorker.class).isEmpty());
        assertFalse(context.getBeansOfType(DbPollingScheduler.class).isEmpty());
        assertFalse(context.getBeansOfType(ImageGenerator.class).isEmpty());
        assertTrue(context.getBeansWithAnnotation(org.springframework.web.bind.annotation.RestController.class).isEmpty());
        assertTrue(context.getBeansOfType(Object.class).values().stream()
                .map(Object::getClass)
                .noneMatch(type -> type.getName().startsWith("com.kng0501.dbpolling.server.")));
        assertEquals(Set.of(ImageGenerationRequestEntity.class), entities);
    }
}
