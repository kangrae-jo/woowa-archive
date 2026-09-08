package com.kng0501.dbpolling.persistence;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.cleanBaseline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbpolling.application.ImageGenerationService;
import com.kng0501.dbpolling.persistence.entity.BaselineImageGenerationRequestEntity;
import com.kng0501.dbpolling.persistence.entity.BaselineMonsterEntity;
import com.kng0501.dbpolling.persistence.jpa.BaselineMonsterJpaRepository;
import com.kng0501.dbqueue.application.JobQueue;
import com.kng0501.dbqueue.persistence.ImageGenerationJobJpaRepository;
import com.kng0501.technicalwriting.testsupport.BaselineIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

@BaselineIntegrationTest
final class BaselineProfileIsolationTest {

    private final ApplicationContext context;
    private final EntityManagerFactory entityManagerFactory;
    private final JdbcTemplate jdbc;

    @Autowired
    BaselineProfileIsolationTest(
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
    void baseline은_02단계_빈_엔티티_Repository만_등록한다() {
        final Set<Class<?>> entities = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(type -> type.getJavaType())
                .collect(Collectors.toSet());

        assertFalse(context.getBeansOfType(ImageGenerationService.class).isEmpty());
        assertFalse(AopUtils.isAopProxy(context.getBean(ImageGenerationService.class)),
                "02 서비스 전체에는 등록 트랜잭션을 추가하면 안 된다.");
        assertFalse(context.getBeansOfType(BaselineMonsterJpaRepository.class).isEmpty());
        assertTrue(context.getBeansOfType(JobQueue.class).isEmpty());
        assertTrue(context.getBeansOfType(ImageGenerationJobJpaRepository.class).isEmpty());
        assertEquals(Set.of(BaselineMonsterEntity.class, BaselineImageGenerationRequestEntity.class), entities);
    }
}
