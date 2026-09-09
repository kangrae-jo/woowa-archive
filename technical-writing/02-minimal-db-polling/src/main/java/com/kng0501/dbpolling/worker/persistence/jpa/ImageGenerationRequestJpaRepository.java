package com.kng0501.dbpolling.worker.persistence.jpa;

import com.kng0501.dbpolling.worker.persistence.entity.ImageGenerationRequestEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface ImageGenerationRequestJpaRepository
        extends JpaRepository<ImageGenerationRequestEntity, Long> {

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    @Query("select request from ImageGenerationRequestEntity request order by request.id")
    List<ImageGenerationRequestEntity> findOldest(Pageable pageable);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ImageGenerationRequestEntity request where request.id = :requestId")
    int deleteRequestById(@Param("requestId") long requestId);
}
