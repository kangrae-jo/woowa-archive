package com.kng0501.dbqueue.domain;

import java.time.Duration;
import java.util.Objects;

public record QueueSettings(
        Duration processingTimeout,
        int maxAttempts,
        Duration retryDelay,
        int concurrency,
        Duration pollingInterval,
        Duration recoveryInterval
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

    public static QueueSettings experimentalDefaults() {
        return new QueueSettings(
                Duration.ofSeconds(30), 3, Duration.ofSeconds(1), 2,
                Duration.ofMillis(100), Duration.ofMillis(100)
        );
    }

    private static void requirePositiveMillis(Duration value, String name) {
        if (Objects.requireNonNull(value, name).toMillis() < 1) {
            throw new IllegalArgumentException(name + "는 1ms 이상이어야 합니다.");
        }
    }
}
