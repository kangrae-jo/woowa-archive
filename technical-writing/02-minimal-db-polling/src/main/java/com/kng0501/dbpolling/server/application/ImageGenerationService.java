package com.kng0501.dbpolling.server.application;

import com.kng0501.dbpolling.server.domain.Monster;
import com.kng0501.dbpolling.server.persistence.entity.ImageGenerationRequestEntity;
import com.kng0501.dbpolling.server.persistence.entity.MonsterEntity;
import com.kng0501.dbpolling.server.persistence.jpa.ImageGenerationRequestJpaRepository;
import com.kng0501.dbpolling.server.persistence.jpa.MonsterJpaRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImageGenerationService {

    private final MonsterJpaRepository monsters;
    private final ImageGenerationRequestJpaRepository requests;
    private final Clock clock;

    public ImageGenerationService(
            final MonsterJpaRepository monsters,
            final ImageGenerationRequestJpaRepository requests,
            final Clock clock
    ) {
        this.monsters = monsters;
        this.requests = requests;
        this.clock = clock;
    }

    public long request(final String prompt) {
        validatePrompt(prompt);
        final MonsterEntity monster = monsters.saveAndFlush(new MonsterEntity(prompt));
        try {
            requests.saveAndFlush(new ImageGenerationRequestEntity(prompt, clock.instant()));
        } catch (final DataAccessException failure) {
            throw new DataIntegrityViolationException("이미지 생성 요청 저장에 실패했습니다.", failure);
        }
        return monster.getId();
    }

    @Transactional(readOnly = true)
    public Optional<Monster> findMonster(final long monsterId) {
        return monsters.findById(monsterId)
                .map(monster -> new Monster(monster.getId(), monster.getPrompt(), monster.getImage()));
    }

    private static void validatePrompt(final String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt는 비어 있을 수 없습니다.");
        }
    }
}
