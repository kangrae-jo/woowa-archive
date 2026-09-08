package com.kng0501.dbqueue.persistence;

import static com.kng0501.technicalwriting.testsupport.MySqlTestDatabase.cleanHardened;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.dbqueue.application.ExpiredJobTransition;
import com.kng0501.dbqueue.application.JobQueue;
import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.JobStatus;
import com.kng0501.dbqueue.domain.QueueSettings;
import com.kng0501.dbqueue.persistence.entity.ImageGenerationJobEntity;
import com.kng0501.dbqueue.persistence.entity.QueueMonsterEntity;
import com.kng0501.dbqueue.support.MutableClock;
import com.kng0501.technicalwriting.testsupport.HardenedIntegrationTest;
import com.kng0501.technicalwriting.testsupport.SqlCaptureInspector;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hibernate.proxy.HibernateProxy;
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

@HardenedIntegrationTest
final class JpaPersistenceContractTest {

    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final QueueSettings settings;
    private final JobQueue queue;
    private final ImageGenerationJobJpaRepository jobs;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;
    private final SqlCaptureInspector sql;

    @Autowired
    JpaPersistenceContractTest(
            final JdbcTemplate jdbc,
            final MutableClock clock,
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
        cleanHardened(jdbc);
        clock.reset();
        sql.clear();
    }

    @AfterEach
    void tearDown() {
        cleanHardened(jdbc);
        clock.reset();
        sql.clear();
    }

    @Test
    void 벌크_UPDATE는_flush와_clear_후_재조회해_오래된_엔티티를_반환하지_않는다() {
        final JobQueue.Registration target = queue.request("dragon");
        final UUID token = UUID.randomUUID();
        final Instant now = clock.instant();
        final var transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            final ImageGenerationJobEntity stale = jobs.findById(target.jobId()).orElseThrow();
            assertTrue(entityManager.contains(stale));

            final int changed = jobs.claim(
                    target.jobId(), token, now, now.plus(settings.processingTimeout()),
                    settings.maxAttempts(), JobStatus.PENDING, JobStatus.RUNNING
            );

            assertEquals(1, changed);
            assertFalse(entityManager.contains(stale), "clearAutomatically로 벌크 UPDATE 전 상태를 분리해야 한다.");
            assertEquals(JobStatus.PENDING, stale.getStatus(), "분리된 객체는 오래된 상태임을 명시한다.");
            assertEquals(JobStatus.RUNNING, jobs.findSnapshotById(target.jobId()).orElseThrow().getStatus());
        });

        assertEquals(JobStatus.RUNNING, queue.findJob(target.jobId()).orElseThrow().status());
    }

    @Test
    void Worker_전달값은_JPA_엔티티나_LAZY_프록시가_아닌_불변_record다() {
        final Job claim = queue.tryClaim(queue.request("dragon").jobId()).orElseThrow();

        assertTrue(Job.class.isRecord());
        assertFalse(HibernateProxy.class.isInstance(claim));
        assertTrue(Arrays.stream(Job.class.getRecordComponents())
                .map(component -> component.getType())
                .noneMatch(type -> type == ImageGenerationJobEntity.class || type == QueueMonsterEntity.class));
        assertNotNull(claim.claimToken());
        assertEquals(JobStatus.RUNNING, claim.status());
    }

    @Test
    void UUID와_UTC_마이크로초_시각을_MySQL_스키마대로_왕복한다() {
        clock.set(Instant.parse("2026-09-06T12:34:56.123456789Z"));
        final Job claim = queue.tryClaim(queue.request("dragon").jobId()).orElseThrow();

        final String rawToken = jdbc.queryForObject(
                "SELECT claim_token FROM image_generation_job WHERE job_id = ?",
                String.class,
                claim.jobId()
        );
        final String rawStartedAt = jdbc.queryForObject(
                "SELECT DATE_FORMAT(started_at, '%Y-%m-%d %H:%i:%s.%f') "
                        + "FROM image_generation_job WHERE job_id = ?",
                String.class,
                claim.jobId()
        );
        final String sessionTimeZone = jdbc.queryForObject("SELECT @@session.time_zone", String.class);

        assertEquals("2026-09-06T12:34:56.123456Z", claim.startedAt().toString());
        assertEquals(claim.claimToken().toString(), rawToken);
        assertEquals("2026-09-06 12:34:56.123456", rawStartedAt);
        assertTrue(sessionTimeZone.equals("+00:00") || sessionTimeZone.equalsIgnoreCase("UTC"));
        assertColumn("deadline_at", "datetime", 6L, null);
        assertColumn("claim_token", "char", null, 36L);
    }

    @Test
    void 실행_조회는_ID_projection을_사용하고_Monster_LAZY_접근시에만_추가_SELECT한다() {
        for (int index = 0; index < 3; index++) {
            queue.request("job-" + index);
        }

        sql.clear();
        queue.findCandidate().orElseThrow();
        final List<String> candidateSql = sql.statements();
        assertEquals(1, candidateSql.size());
        assertTrue(candidateSql.getFirst().toLowerCase().startsWith("select"));
        assertFalse(candidateSql.getFirst().toLowerCase().contains("join queue_monster"));

        final var transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.executeWithoutResult(status -> {
            sql.clear();
            final List<ImageGenerationJobEntity> loaded = jobs.findAllForQueryPlan();
            assertEquals(1, sql.statements().size());

            loaded.forEach(job -> job.getMonster().getId());
            assertEquals(1, sql.statements().size(), "프록시 식별자 접근은 Monster SELECT를 만들지 않는다.");

            loaded.forEach(job -> job.getMonster().getPrompt());
            assertEquals(4, sql.statements().size(), "서로 다른 Monster 3건에 실제 접근하면 SELECT 3회가 추가된다.");
        });
    }

    @Test
    void 선점과_완료의_실제_SQL_순서를_고정한다() {
        final JobQueue.Registration target = queue.request("dragon");

        sql.clear();
        final Job claim = queue.tryClaim(target.jobId()).orElseThrow();
        final List<String> claimSql = sql.statements();
        final int claimUpdate = indexOf(claimSql, "update image_generation_job");
        final int claimReload = indexOf(claimSql, "select");
        assertTrue(claimUpdate >= 0);
        assertTrue(claimReload > claimUpdate);
        assertTrue(claimSql.stream().noneMatch(statement -> statement.toLowerCase().contains("join queue_monster")));

        sql.clear();
        assertTrue(queue.complete(claim.jobId(), claim.claimToken(), "image:dragon"));
        final List<String> completionSql = sql.statements();
        final int jobUpdate = indexOf(completionSql, "update image_generation_job");
        final int monsterLookup = indexOf(completionSql, "from queue_monster");
        final int monsterUpdate = indexOf(completionSql, "update queue_monster");
        assertTrue(jobUpdate >= 0);
        assertTrue(monsterLookup > jobUpdate);
        assertTrue(monsterUpdate > monsterLookup);
    }

    @Test
    void MySQL_스키마는_FK_CHECK와_준비_기한_인덱스를_보존한다() {
        final Integer foreignKeyCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM information_schema.REFERENTIAL_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'image_generation_job'
                   AND CONSTRAINT_NAME = 'fk_job_monster'
                """, Integer.class);
        final Integer checkCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM information_schema.TABLE_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'image_generation_job'
                   AND CONSTRAINT_TYPE = 'CHECK'
                """, Integer.class);
        final Integer indexCount = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT INDEX_NAME)
                  FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'image_generation_job'
                   AND INDEX_NAME IN ('job_ready_idx', 'job_deadline_idx')
                """, Integer.class);

        assertEquals(1, foreignKeyCount);
        assertEquals(4, checkCount);
        assertEquals(2, indexCount);
    }

    @Test
    void 변경_서비스와_개별_복구는_REQUIRES_NEW를_선언한다() throws NoSuchMethodException {
        assertRequiresNew(JobQueue.class.getMethod("request", String.class));
        assertRequiresNew(JobQueue.class.getMethod("tryClaim", long.class));
        assertRequiresNew(JobQueue.class.getMethod("complete", long.class, UUID.class, String.class));
        assertRequiresNew(JobQueue.class.getMethod("fail", Job.class, Throwable.class));
        assertRequiresNew(ExpiredJobTransition.class.getMethod("recover", Job.class, Instant.class));
    }

    private void assertColumn(
            final String column,
            final String expectedType,
            final Long expectedDateTimePrecision,
            final Long expectedLength
    ) {
        final ColumnMetadata metadata = jdbc.queryForObject("""
                SELECT DATA_TYPE, DATETIME_PRECISION, CHARACTER_MAXIMUM_LENGTH
                  FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'image_generation_job'
                   AND COLUMN_NAME = ?
                """, (resultSet, rowNumber) -> new ColumnMetadata(
                resultSet.getString("DATA_TYPE"),
                nullableLong(resultSet, "DATETIME_PRECISION"),
                nullableLong(resultSet, "CHARACTER_MAXIMUM_LENGTH")
        ), column);
        assertNotNull(metadata);
        assertEquals(expectedType, metadata.dataType());
        assertEquals(expectedDateTimePrecision, metadata.dateTimePrecision());
        assertEquals(expectedLength, metadata.maximumLength());
    }

    private static Long nullableLong(final java.sql.ResultSet resultSet, final String column)
            throws java.sql.SQLException {
        final long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static int indexOf(final List<String> statements, final String token) {
        final String lowerToken = token.toLowerCase();
        for (int index = 0; index < statements.size(); index++) {
            if (statements.get(index).toLowerCase().contains(lowerToken)) {
                return index;
            }
        }
        return -1;
    }

    private static void assertRequiresNew(final Method method) {
        final var source = new AnnotationTransactionAttributeSource();
        final TransactionAttribute attribute = source.getTransactionAttribute(method, method.getDeclaringClass());
        assertNotNull(attribute);
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, attribute.getPropagationBehavior());
    }

    private record ColumnMetadata(String dataType, Long dateTimePrecision, Long maximumLength) {
    }
}
