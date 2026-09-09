package com.kng0501.dbqueue.worker;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.clean04;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbqueue.worker.application.ExpiredJobRecovery;
import com.kng0501.dbqueue.worker.application.JobQueue;
import com.kng0501.dbqueue.worker.application.JobScheduler;
import com.kng0501.dbqueue.worker.application.JobWorker;
import com.kng0501.dbqueue.worker.domain.ImageGenerator;
import com.kng0501.dbqueue.worker.persistence.entity.ImageGenerationJobEntity;
import com.kng0501.dbqueue.worker.persistence.jpa.ImageGenerationJobJpaRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

@WorkerIntegrationTest
final class WorkerIsolationTest {

    private final ApplicationContext context;
    private final EntityManagerFactory entityManagerFactory;
    private final JobQueue queue;
    private final JdbcTemplate jdbc;

    @Autowired
    WorkerIsolationTest(
            final ApplicationContext context,
            final EntityManagerFactory entityManagerFactory,
            final JobQueue queue,
            final JdbcTemplate jdbc
    ) {
        this.context = context;
        this.entityManagerFactory = entityManagerFactory;
        this.queue = queue;
        this.jdbc = jdbc;
    }

    @AfterEach
    void tearDown() {
        clean04(jdbc);
    }

    @Test
    void 워커는_자신의_작업_Bean과_Job_영속성만_등록한다() {
        final Set<Class<?>> entities = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(type -> type.getJavaType())
                .collect(Collectors.toSet());

        assertFalse(context.getBeansOfType(JobQueue.class).isEmpty());
        assertFalse(context.getBeansOfType(ImageGenerationJobJpaRepository.class).isEmpty());
        assertFalse(context.getBeansOfType(JobWorker.class).isEmpty());
        assertFalse(context.getBeansOfType(JobScheduler.class).isEmpty());
        assertFalse(context.getBeansOfType(ExpiredJobRecovery.class).isEmpty());
        assertFalse(context.getBeansOfType(ImageGenerator.class).isEmpty());
        assertTrue(context.getBeansWithAnnotation(org.springframework.web.bind.annotation.RestController.class).isEmpty());
        assertTrue(context.getBeansOfType(Object.class).values().stream()
                .map(Object::getClass)
                .noneMatch(type -> type.getName().startsWith("com.kng0501.dbqueue.server.")));
        assertEquals(Set.of(ImageGenerationJobEntity.class), entities);
        assertTrue(AopUtils.isAopProxy(queue), "@Transactional 서비스는 Spring 프록시여야 한다.");
    }
}
