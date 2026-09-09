package com.kng0501.technicalwriting.testsupport;

import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

public final class MySqlTestDatabase {

    private MySqlTestDatabase() {
    }

    public static void clean02(final JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE image_generation_request");
        jdbc.execute("TRUNCATE TABLE monster");
    }

    public static void clean04(final JdbcTemplate jdbc) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            execute(connection, "SET FOREIGN_KEY_CHECKS = 0");
            try {
                execute(connection, "TRUNCATE TABLE image_generation_job");
                execute(connection, "TRUNCATE TABLE queue_monster");
            } finally {
                execute(connection, "SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
    }

    public static int count(final JdbcTemplate jdbc, final String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    public static void dropCheckIfExists(
            final JdbcTemplate jdbc,
            final String table,
            final String constraint
    ) {
        final Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM information_schema.TABLE_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE()
                   AND TABLE_NAME = ?
                   AND CONSTRAINT_NAME = ?
                   AND CONSTRAINT_TYPE = 'CHECK'
                """, Integer.class, table, constraint);
        if (count != null && count == 1) {
            jdbc.execute("ALTER TABLE " + table + " DROP CHECK " + constraint);
        }
    }

    public static void deleteMonsterWithoutForeignKeyCheck(final JdbcTemplate jdbc, final long monsterId) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            execute(connection, "SET FOREIGN_KEY_CHECKS = 0");
            try {
                execute(connection, "DELETE FROM queue_monster WHERE monster_id = " + monsterId);
            } finally {
                execute(connection, "SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
    }

    private static void execute(final Connection connection, final String sql) throws SQLException {
        try (final var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
