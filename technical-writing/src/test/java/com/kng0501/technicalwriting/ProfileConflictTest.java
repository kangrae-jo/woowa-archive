package com.kng0501.technicalwriting;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kng0501.technicalwriting.config.ProfileConflictConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

final class ProfileConflictTest {

    @Test
    void baseline과_hardened를_동시에_활성화하면_명확히_거부한다() {
        try (final var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("baseline", "hardened");
            context.register(ProfileConflictConfiguration.class);

            final IllegalStateException failure = assertThrows(IllegalStateException.class, context::refresh);

            assertTrue(rootMessage(failure).contains("동시에 활성화할 수 없습니다"));
        }
    }

    private static String rootMessage(final Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }
}
