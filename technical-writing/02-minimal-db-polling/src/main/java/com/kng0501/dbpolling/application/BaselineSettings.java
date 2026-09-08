package com.kng0501.dbpolling.application;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "baseline")
public record BaselineSettings(Duration pollingInterval, boolean schedulingEnabled) {

    public BaselineSettings {
        if (Objects.requireNonNull(pollingInterval).toMillis() < 1) {
            throw new IllegalArgumentException("pollingInterval은 1ms 이상이어야 합니다.");
        }
    }
}
