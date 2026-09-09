package com.kng0501.dbqueue.worker.persistence;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.clean04;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbqueue.worker.TestClock;
import com.kng0501.dbqueue.worker.WorkerIntegrationTest;
import com.kng0501.dbqueue.worker.WorkerTestData;
import com.kng0501.dbqueue.worker.application.ExpiredJobTransition;
import com.kng0501.dbqueue.worker.application.JobQueue;
import com.kng0501.dbqueue.worker.domain.Job;
import com.kng0501.dbqueue.worker.domain.JobStatus;
import com.kng0501.dbqueue.worker.domain.QueueSettings;
import com.kng0501.dbqueue.worker.persistence.entity.ImageGenerationJobEntity;
import com.kng0501.dbqueue.worker.persistence.jpa.ImageGenerationJobJpaRepository;
import com.kng0501.technicalwriting.testsupport.SqlCaptureInspector;
import jakarta.persistence.EntityManager;
import jakarta.persistence.ManyToOne;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.support.TransactionTemplate;

@WorkerIntegrationTest
final class JpaPersistenceContractTest {

    private final JdbcTemplate jdbc;
    private final TestClock clock;
    private final QueueSettings settings;
    private final JobQueue queue;
    private final ImageGenerationJobJpaRepository jobs;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;
    private final SqlCaptureInspector sql;

    @Autowired
    JpaPersistenceContractTest(
            final JdbcTemplate jdbc,
            final TestClock clock,
            final QueueSettings settings,
            final JobQueue queue,
            final ImageGenerationJobJpaRepository jobs,
            final EntityManager entityManager,
            final PlatformTransactionManager transactionManager,
            final SqlCaptureInspector sql
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
        this.queue = queue;
        this.jobs = jobs;
        this.entityManager = entityManager;
        this.transactionManager = transactionManager;
        this.sql = sql;
    }

    @BeforeEach
    void setUp() {
        clean04(jdbc);
        clock.reset();
        sql.clear();
    }

    @AfterEach
    void tearDown() {
        clean04(jdbc);
        clock.reset();
        sql.clear();
    }

    @Test
    void Worker_Job_매핑은_Monster_연관관계_없이_monsterId만_가진다() throws NoSuchFieldException {
        assertNotNull(ImageGenerationJobEntity.class.getDeclaredField("monsterId"));
        assertTrue(Arrays.stream(ImageGenerationJobEntity.class.getDeclaredFields())
                .noneMatch(field -> field.isAnnotationPresent(ManyToOne.class)));
        assertTrue(Job.class.isRecord());
        assertTrue(Arrays.stream(Job.class.getRecordComponents())
                .noneMatch(component -> component.getType() == ImageGenerationJobEntity.class));
    }

    @Test
    void 벌크_UPDATE는_flush와_clear_후_재조회해_오래된_엔티티를_반환하지_않는다() {
        final WorkerTestData.JobData target = WorkerTestData.register(jdbc, "dragon", clock.instant());
        final UUID token = UUID.randomUUID();
        final Instant now = clock.instant();
        final TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            final ImageGenerationJobEntity stale = jobs.findById(target.jobId()).orElseThrow();
            assertTrue(entityManager.contains(stale));

            final int changed = jobs.claim(
                    target.jobId(), token, now, now.plus(settings.processingTimeout()),
                    settings.maxAttempts(), JobStatus.PENDING, JobStatus.RUNNING
            );

            assertEquals(1, changed);
            assertFalse(entityManager.contains(stale), "clearAutomatically로 벌크 UPDATE 전 상태를 분리해야 한다.");
            assertEquals(JobStatus.PENDING, stale.getStatus());
            assertEquals(JobStatus.RUNNING, jobs.findSnapshotById(target.jobId()).orElseThrow().getStatus());
        });

        assertEquals(JobStatus.RUNNING, queue.findJob(target.jobId()).orElseThrow().status());
    }

    @Test
    void 선점과_완료는_Job_테이블과_명시적_Monster_ID_갱신만_사용한다() {
        final WorkerTestData.JobData target = WorkerTestData.register(jdbc, "dragon", clock.instant());

        sql.clear();
        final Job claim = queue.tryClaim(target.jobId()).orElseThrow();
        final List<String> claimSql = sql.statements();
        assertTrue(claimSql.stream().anyMatch(statement -> statement.toLowerCase().contains("update image_generation_job")));
        assertTrue(claimSql.stream().noneMatch(statement -> statement.toLowerCase().contains("join queue_monster")));

        sql.clear();
        assertTrue(queue.complete(claim.jobId(), claim.claimToken(), "image:dragon"));
        final List<String> completionSql = sql.statements();
        assertTrue(completionSql.stream().anyMatch(statement -> statement.toLowerCase().contains("update image_generation_job")));
        assertTrue(completionSql.stream().anyMatch(statement -> statement.toLowerCase().contains("update queue_monster")));
        assertTrue(completionSql.stream().noneMatch(statement -> statement.toLowerCase().contains("join queue_monster")));
    }

    @Test
    void Worker_변경과_복구_전환은_REQUIRES_NEW를_선언한다() throws NoSuchMethodException {
        assertRequiresNew(JobQueue.class.getMethod("tryClaim", long.class));
        assertRequiresNew(JobQueue.class.getMethod("complete", long.class, UUID.class, String.class));
        assertRequiresNew(JobQueue.class.getMethod("fail", Job.class, Throwable.class));
        assertRequiresNew(ExpiredJobTransition.class.getMethod("recover", Job.class, Instant.class));
    }

    private static void assertRequiresNew(final Method method) {
        final AnnotationTransactionAttributeSource source = new AnnotationTransactionAttributeSource();
        final TransactionAttribute attribute = source.getTransactionAttribute(method, method.getDeclaringClass());
        assertNotNull(attribute);
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, attribute.getPropagationBehavior());
    }
}
