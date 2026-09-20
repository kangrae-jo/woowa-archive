package com.kng0501.measurement;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MeasurementDatabase {

    public static final String MEASUREMENT_DATABASE_NAME = "technical_writing_measurement";

    private final JdbcTemplate jdbc;

    public MeasurementDatabase(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String verifyMeasurementDatabaseIsEmpty() {
        final String databaseName = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (!MEASUREMENT_DATABASE_NAME.equals(databaseName)) {
            throw new IllegalStateException(
                    "측정 전용 DB가 아닙니다. expected=" + MEASUREMENT_DATABASE_NAME + ", actual=" + databaseName
            );
        }

        final int monsterCount = count("queue_monster");
        final int jobCount = count("image_generation_job");
        if (monsterCount != 0 || jobCount != 0) {
            throw new IllegalStateException(
                    "측정 전용 DB의 04 테이블이 비어 있지 않습니다. queue_monster=" + monsterCount
                            + ", image_generation_job=" + jobCount
            );
        }
        return databaseName;
    }

    public String serverVersion() {
        return jdbc.queryForObject("SELECT VERSION()", String.class);
    }

    public List<ObservedJob> findJobs(final String runId) {
        return jdbc.query(
                """
                        SELECT j.job_id, j.monster_id, j.prompt, j.status, j.attempt_count,
                               j.next_attempt_at, j.deadline_at, j.created_at, j.started_at, j.finished_at,
                               j.last_error, m.image
                        FROM image_generation_job j
                        LEFT JOIN queue_monster m ON m.monster_id = j.monster_id
                        WHERE j.prompt LIKE ?
                        ORDER BY j.job_id
                        """,
                this::mapJob,
                "measurement-" + runId + "-%"
        );
    }

    private int count(final String tableName) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    private ObservedJob mapJob(final ResultSet resultSet, final int rowNumber) throws SQLException {
        return new ObservedJob(
                resultSet.getLong("job_id"),
                resultSet.getLong("monster_id"),
                resultSet.getString("prompt"),
                JobStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempt_count"),
                instant(resultSet, "next_attempt_at"),
                instant(resultSet, "deadline_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "started_at"),
                instant(resultSet, "finished_at"),
                resultSet.getString("last_error"),
                resultSet.getString("image")
        );
    }

    private static Instant instant(final ResultSet resultSet, final String column) throws SQLException {
        final Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
