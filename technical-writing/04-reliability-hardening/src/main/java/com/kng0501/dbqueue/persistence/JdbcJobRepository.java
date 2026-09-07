package com.kng0501.dbqueue.persistence;

import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.JobStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcJobRepository {
    private final JdbcTemplate jdbc;

    public JdbcJobRepository(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long enqueue(final long monsterId, final String prompt, final Instant now) {
        final var key = new GeneratedKeyHolder();
        final int count = jdbc.update(connection -> {
            final var statement = connection.prepareStatement("""
                    INSERT INTO image_generation_job(monster_id, prompt, status, next_attempt_at, created_at)
                    VALUES (?, ?, 'PENDING', ?, ?)
                    """, new String[]{"job_id"});
            statement.setLong(1, monsterId);
            statement.setString(2, prompt);
            statement.setObject(3, timestamp(now));
            statement.setObject(4, timestamp(now));
            return statement;
        }, key);
        if (count != 1 || key.getKey() == null) {
            throw new IllegalStateException("Job 등록 실패");
        }
        return key.getKey().longValue();
    }

    public Optional<Long> findCandidate(final Instant now, final int maxAttempts) {
        return jdbc.query("""
                SELECT job_id FROM image_generation_job
                WHERE status = 'PENDING' AND next_attempt_at <= ? AND attempt_count < ?
                ORDER BY next_attempt_at, job_id LIMIT 1
                """, (rs, row) -> rs.getLong("job_id"), timestamp(now), maxAttempts).stream().findFirst();
    }

    public boolean claim(final long jobId, final UUID token, final Instant now, final Instant deadline, final int maxAttempts) {
        return jdbc.update("""
                UPDATE image_generation_job
                SET status = 'RUNNING', claim_token = ?, deadline_at = ?,
                    attempt_count = attempt_count + 1, started_at = ?, finished_at = NULL
                WHERE job_id = ? AND status = 'PENDING' AND next_attempt_at <= ? AND attempt_count < ?
                """, token, timestamp(deadline), timestamp(now), jobId, timestamp(now), maxAttempts) == 1;
    }

    public boolean markSucceeded(final long jobId, final UUID token, final Instant now) {
        return jdbc.update("""
                UPDATE image_generation_job
                SET status = 'SUCCEEDED', finished_at = ?, claim_token = NULL, deadline_at = NULL
                WHERE job_id = ? AND status = 'RUNNING' AND claim_token = ? AND deadline_at > ?
                """, timestamp(now), jobId, token, timestamp(now)) == 1;
    }

    public boolean fail(final Job claim, final Instant now, final Instant nextAttemptAt, final int maxAttempts, final String reason) {
        return transitionFailure(claim, now, nextAttemptAt, maxAttempts, reason, "deadline_at > ?");
    }

    public boolean expire(final Job claim, final Instant now, final Instant nextAttemptAt, final int maxAttempts) {
        return transitionFailure(claim, now, nextAttemptAt, maxAttempts, "processing deadline expired", "deadline_at <= ?");
    }

    private boolean transitionFailure(
            final Job claim, final Instant now, final Instant nextAttemptAt, final int maxAttempts, final String reason, final String deadlineCondition
    ) {
        return jdbc.update("""
                UPDATE image_generation_job
                SET status = CASE WHEN attempt_count >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    next_attempt_at = CASE WHEN attempt_count >= ? THEN next_attempt_at ELSE ? END,
                    finished_at = CASE WHEN attempt_count >= ? THEN ? ELSE NULL END,
                    claim_token = NULL, deadline_at = NULL, last_error = ?
                WHERE job_id = ? AND status = 'RUNNING' AND claim_token = ? AND """ + " " + deadlineCondition,
                maxAttempts, maxAttempts, timestamp(nextAttemptAt), maxAttempts, timestamp(now),
                reason, claim.jobId(), claim.claimToken(), timestamp(now)
        ) == 1;
    }

    public List<Job> findExpired(final Instant now, final int limit) {
        return jdbc.query("""
                SELECT * FROM image_generation_job
                WHERE status = 'RUNNING' AND deadline_at <= ?
                ORDER BY deadline_at, job_id LIMIT ?
                """, this::map, timestamp(now), limit);
    }

    public Optional<Job> findById(final long jobId) {
        return jdbc.query(
                "SELECT * FROM image_generation_job WHERE job_id = ?", this::map, jobId
        ).stream().findFirst();
    }

    private Job map(final ResultSet rs, final int row) throws SQLException {
        return new Job(
                rs.getLong("job_id"), rs.getLong("monster_id"), rs.getString("prompt"),
                JobStatus.valueOf(rs.getString("status")), rs.getInt("attempt_count"),
                instant(rs, "next_attempt_at"), instant(rs, "deadline_at"),
                rs.getObject("claim_token", UUID.class), instant(rs, "created_at"),
                instant(rs, "started_at"), instant(rs, "finished_at"), rs.getString("last_error")
        );
    }

    private static OffsetDateTime timestamp(final Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(final ResultSet rs, final String column) throws SQLException {
        final OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
