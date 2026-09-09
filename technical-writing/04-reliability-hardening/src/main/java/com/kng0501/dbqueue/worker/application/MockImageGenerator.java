package com.kng0501.dbqueue.worker.application;

import com.kng0501.dbqueue.worker.domain.ImageGenerator;

public final class MockImageGenerator implements ImageGenerator {

    private final MockImageGeneratorSettings settings;

    public MockImageGenerator(final MockImageGeneratorSettings settings) {
        this.settings = settings;
    }

    @Override
    public String generate(final String prompt) throws InterruptedException {
        if (!settings.delay().isZero()) {
            Thread.sleep(settings.delay());
        }
        return "image:" + prompt;
    }
}
