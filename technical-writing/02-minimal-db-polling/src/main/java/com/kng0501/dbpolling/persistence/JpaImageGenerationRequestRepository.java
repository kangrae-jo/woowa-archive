package com.kng0501.dbpolling.persistence;

import com.kng0501.dbpolling.domain.ImageGenerationRequest;
import com.kng0501.dbpolling.persistence.entity.BaselineImageGenerationRequestEntity;
import com.kng0501.dbpolling.persistence.jpa.BaselineImageGenerationRequestJpaRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaImageGenerationRequestRepository implements ImageGenerationRequestRepository {

    private final BaselineImageGenerationRequestJpaRepository repository;
    private final Clock clock;

    public JpaImageGenerationRequestRepository(
            final BaselineImageGenerationRequestJpaRepository repository,
            final Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long enqueue(final String prompt) {
        validatePrompt(prompt);
        final var request = new BaselineImageGenerationRequestEntity(prompt, clock.instant());
        return repository.saveAndFlush(request).getId();
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<ImageGenerationRequest> findOldest() {
        return repository.findOldest(PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(entity -> new ImageGenerationRequest(entity.getId(), entity.getPrompt()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteById(final long requestId) {
        repository.deleteRequestById(requestId);
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public long count() {
        return repository.count();
    }

    private static void validatePrompt(final String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt는 비어 있을 수 없습니다.");
        }
    }
}
