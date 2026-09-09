package com.kng0501.dbpolling.worker;

import org.springframework.jdbc.core.JdbcTemplate;

public final class WorkerTestData {

    private WorkerTestData() {
    }

    public static long register(final JdbcTemplate jdbc, final String prompt) {
        final long monsterId = saveMonster(jdbc, prompt);
        enqueueRequest(jdbc, prompt);
        return monsterId;
    }

    public static long saveMonster(final JdbcTemplate jdbc, final String prompt) {
        jdbc.update("INSERT INTO monster(prompt) VALUES (?)", prompt);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public static long enqueueRequest(final JdbcTemplate jdbc, final String prompt) {
        jdbc.update("INSERT INTO image_generation_request(prompt, created_at) VALUES (?, UTC_TIMESTAMP(6))", prompt);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
