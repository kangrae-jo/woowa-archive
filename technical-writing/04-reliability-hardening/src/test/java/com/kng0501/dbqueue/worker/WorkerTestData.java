package com.kng0501.dbqueue.worker;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

public final class WorkerTestData {

    private WorkerTestData() {
    }

    public static JobData register(final JdbcTemplate jdbc, final String prompt, final Instant now) {
        final long monsterId = insertMonster(jdbc, prompt);
        final long jobId = insertPendingJob(jdbc, monsterId, prompt, now);
        return new JobData(jobId, monsterId);
    }

    public static long insertMonster(final JdbcTemplate jdbc, final String prompt) {
        jdbc.update("INSERT INTO queue_monster(prompt) VALUES (?)", prompt);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public static long insertPendingJob(
            final JdbcTemplate jdbc,
            final long monsterId,
            final String prompt,
            final Instant now
    ) {
        final Timestamp timestamp = Timestamp.from(now);
        jdbc.update("""
                INSERT INTO image_generation_job(
                    monster_id, prompt, status, attempt_count, next_attempt_at, created_at
                ) VALUES (?, ?, 'PENDING', 0, ?, ?)
                """, monsterId, prompt, timestamp, timestamp);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public record JobData(long jobId, long monsterId) {
    }
}
