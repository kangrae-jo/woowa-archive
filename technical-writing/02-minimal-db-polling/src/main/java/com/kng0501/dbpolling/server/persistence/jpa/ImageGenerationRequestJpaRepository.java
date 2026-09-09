package com.kng0501.dbpolling.server.persistence.jpa;

import com.kng0501.dbpolling.server.persistence.entity.ImageGenerationRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageGenerationRequestJpaRepository
        extends JpaRepository<ImageGenerationRequestEntity, Long> {
}
