package com.kng0501.dbpolling.persistence;

import com.kng0501.dbpolling.domain.ImageGenerationRequest;
import java.util.Optional;

public interface ImageGenerationRequestRepository {

    long enqueue(String prompt);

    Optional<ImageGenerationRequest> findOldest();

    void deleteById(long requestId);

    long count();
}
