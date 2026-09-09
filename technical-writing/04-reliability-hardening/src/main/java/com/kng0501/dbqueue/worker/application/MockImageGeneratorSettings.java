package com.kng0501.dbqueue.worker.application;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "image-generator")
public record MockImageGeneratorSettings(Duration delay) {

    public MockImageGeneratorSettings {
        if (Objects.requireNonNull(delay, "delay").isNegative()) {
            throw new IllegalArgumentException("image-generator.delay는 0 이상이어야 합니다.");
        }
    }
}
