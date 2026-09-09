package com.kng0501.dbqueue.worker.domain;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "db-queue")
public record QueueSettings(
        Duration processingTimeout,
        int maxAttempts,
        Duration retryDelay,
        int concurrency,
        Duration pollingInterval,
        Duration recoveryInterval,
        boolean schedulingEnabled
) {
    public QueueSettings {
        requirePositiveMillis(processingTimeout, "processingTimeout");
        requirePositiveMillis(pollingInterval, "pollingInterval");
        requirePositiveMillis(recoveryInterval, "recoveryInterval");
        Objects.requireNonNull(retryDelay, "retryDelay");
        if (retryDelay.isNegative() || maxAttempts < 1 || concurrency < 1) {
            throw new IllegalArgumentException("retryDelay >= 0, maxAttempts >= 1, concurrency >= 1이어야 합니다.");
        }
    }

    private static void requirePositiveMillis(final Duration value, final String name) {
        if (Objects.requireNonNull(value, name).toMillis() < 1) {
            throw new IllegalArgumentException(name + "는 1ms 이상이어야 합니다.");
        }
    }
}
