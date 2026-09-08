package com.kng0501.technicalwriting.testsupport;

import com.kng0501.dbqueue.domain.ImageGenerator;
import java.util.concurrent.atomic.AtomicReference;

public final class HardenedTestImageGenerator implements ImageGenerator {

    @FunctionalInterface
    public interface Behavior {
        String generate(String prompt) throws Exception;
    }

    private final AtomicReference<Behavior> behavior = new AtomicReference<>(defaultBehavior());

    @Override
    public String generate(final String prompt) throws Exception {
        return behavior.get().generate(prompt);
    }

    public void use(final Behavior nextBehavior) {
        behavior.set(nextBehavior);
    }

    public void reset() {
        behavior.set(defaultBehavior());
    }

    private static Behavior defaultBehavior() {
        return prompt -> "image:" + prompt;
    }
}
