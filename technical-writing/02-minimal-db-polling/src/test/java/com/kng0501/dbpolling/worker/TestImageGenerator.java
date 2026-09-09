package com.kng0501.dbpolling.worker;

import com.kng0501.dbpolling.worker.domain.ImageGenerator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class TestImageGenerator implements ImageGenerator {

    private final AtomicReference<Function<String, String>> behavior = new AtomicReference<>(defaultBehavior());

    @Override
    public String generate(final String prompt) {
        return behavior.get().apply(prompt);
    }

    public void use(final Function<String, String> nextBehavior) {
        behavior.set(nextBehavior);
    }

    public void reset() {
        behavior.set(defaultBehavior());
    }

    private static Function<String, String> defaultBehavior() {
        return prompt -> "image:" + prompt;
    }
}
