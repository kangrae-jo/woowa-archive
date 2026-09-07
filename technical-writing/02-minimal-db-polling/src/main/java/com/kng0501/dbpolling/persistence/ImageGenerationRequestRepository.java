package com.kng0501.dbpolling.persistence;

import com.kng0501.dbpolling.domain.ImageGenerationRequest;
import java.util.Optional;

public interface ImageGenerationRequestRepository {

    long enqueue(final String prompt);

    Optional<ImageGenerationRequest> findOldest();

    void deleteById(final long requestId);

    long count();
}
