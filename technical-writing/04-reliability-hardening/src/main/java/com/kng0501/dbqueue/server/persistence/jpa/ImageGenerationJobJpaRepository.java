package com.kng0501.dbqueue.server.persistence.jpa;

import com.kng0501.dbqueue.server.persistence.entity.ImageGenerationJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageGenerationJobJpaRepository extends JpaRepository<ImageGenerationJobEntity, Long> {
}
