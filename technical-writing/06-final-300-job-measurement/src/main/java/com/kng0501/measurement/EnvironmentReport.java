package com.kng0501.measurement;

import java.time.Duration;
import java.time.Instant;

public record EnvironmentReport(
        String runId,
        Instant startedAt,
        String baseUrl,
        String databaseName,
        int jobCount,
        int requestConcurrency,
        Duration timeout,
        Duration pollingInterval,
        int expectedWorkerConcurrency,
        Duration expectedGeneratorDelay,
        String javaVersion,
        String osName,
        String osVersion,
        String osArchitecture,
        String mysqlVersion
) {
}
