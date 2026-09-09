package com.kng0501.dbpolling.worker.application;

import com.kng0501.dbpolling.worker.domain.ImageGenerator;

public final class MockImageGenerator implements ImageGenerator {

    private final MockImageGeneratorSettings settings;

    public MockImageGenerator(final MockImageGeneratorSettings settings) {
        this.settings = settings;
    }

    @Override
    public String generate(final String prompt) {
        try {
            if (!settings.delay().isZero()) {
                Thread.sleep(settings.delay());
            }
            return "image:" + prompt;
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("모의 이미지 생성을 중단했습니다.", interrupted);
        }
    }
}
