package com.kng0501.dbqueue.server.application;

import com.kng0501.dbqueue.server.persistence.entity.ImageGenerationJobEntity;
import com.kng0501.dbqueue.server.persistence.entity.MonsterEntity;
import com.kng0501.dbqueue.server.persistence.jpa.ImageGenerationJobJpaRepository;
import com.kng0501.dbqueue.server.persistence.jpa.MonsterJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobRegistrationService {

    private final Clock clock;
    private final MonsterJpaRepository monsters;
    private final ImageGenerationJobJpaRepository jobs;

    public JobRegistrationService(
            final Clock clock,
            final MonsterJpaRepository monsters,
            final ImageGenerationJobJpaRepository jobs
    ) {
        this.clock = clock;
        this.monsters = monsters;
        this.jobs = jobs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Registration request(final String prompt) {
        validatePrompt(prompt);
        final Instant now = now();
        final MonsterEntity monster = monsters.save(new MonsterEntity(prompt));
        final ImageGenerationJobEntity job = jobs.saveAndFlush(new ImageGenerationJobEntity(monster, prompt, now));
        return new Registration(job.getId(), monster.getId());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static void validatePrompt(final String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt는 비어 있을 수 없습니다.");
        }
    }

    public record Registration(long jobId, long monsterId) {
    }
}
