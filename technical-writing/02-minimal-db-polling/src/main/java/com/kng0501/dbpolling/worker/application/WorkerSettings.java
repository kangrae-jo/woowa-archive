package com.kng0501.dbpolling.worker.application;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "db-polling-worker")
public record WorkerSettings(Duration pollingInterval, boolean schedulingEnabled) {

    public WorkerSettings {
        if (Objects.requireNonNull(pollingInterval).toMillis() < 1) {
            throw new IllegalArgumentException("pollingInterval은 1ms 이상이어야 합니다.");
        }
    }
}
