package com.kng0501.dbqueue.persistence;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.cleanHardened;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbpolling.application.ImageGenerationService;
import com.kng0501.dbpolling.persistence.jpa.BaselineMonsterJpaRepository;
import com.kng0501.dbqueue.application.JobQueue;
import com.kng0501.dbqueue.persistence.entity.ImageGenerationJobEntity;
import com.kng0501.dbqueue.persistence.entity.QueueMonsterEntity;
import com.kng0501.technicalwriting.testsupport.HardenedIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

@HardenedIntegrationTest
final class HardenedProfileIsolationTest {

    private final ApplicationContext context;
    private final EntityManagerFactory entityManagerFactory;
    private final JobQueue queue;
    private final JdbcTemplate jdbc;

    @Autowired
    HardenedProfileIsolationTest(
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
        cleanHardened(jdbc);
    }

    @Test
    void hardened는_04단계_빈_엔티티_Repository만_등록한다() {
        final Set<Class<?>> entities = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(type -> type.getJavaType())
                .collect(Collectors.toSet());

        assertFalse(context.getBeansOfType(JobQueue.class).isEmpty());
        assertFalse(context.getBeansOfType(ImageGenerationJobJpaRepository.class).isEmpty());
        assertTrue(context.getBeansOfType(ImageGenerationService.class).isEmpty());
        assertTrue(context.getBeansOfType(BaselineMonsterJpaRepository.class).isEmpty());
        assertEquals(Set.of(QueueMonsterEntity.class, ImageGenerationJobEntity.class), entities);
        assertTrue(AopUtils.isAopProxy(queue), "@Transactional 서비스는 Spring 프록시여야 한다.");
    }
}
