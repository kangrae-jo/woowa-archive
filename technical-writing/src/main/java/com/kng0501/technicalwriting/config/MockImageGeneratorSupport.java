package com.kng0501.technicalwriting.config;

import java.time.Duration;

public final class MockImageGeneratorSupport {

    private MockImageGeneratorSupport() {
    }

    public static String generate(final String prompt, final Duration delay) throws InterruptedException {
        if (!delay.isZero()) {
            Thread.sleep(delay);
        }
        return "image:" + prompt;
    }
}
