package com.kng0501.dbpolling.persistence;

import com.kng0501.dbpolling.domain.Monster;
import com.kng0501.dbpolling.persistence.entity.BaselineMonsterEntity;
import com.kng0501.dbpolling.persistence.jpa.BaselineMonsterJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaMonsterRepository implements MonsterRepository {

    private final BaselineMonsterJpaRepository repository;

    public JpaMonsterRepository(final BaselineMonsterJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long save(final String prompt) {
        validateText(prompt, "prompt");
        return repository.saveAndFlush(new BaselineMonsterEntity(prompt)).getId();
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<Monster> findById(final long id) {
        return repository.findById(id)
                .map(entity -> new Monster(entity.getId(), entity.getPrompt(), entity.getImage()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateImage(final long monsterId, final String image) {
        validateText(image, "image");
        repository.updateImage(monsterId, image);
    }

    private static void validateText(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다.");
        }
    }
}
