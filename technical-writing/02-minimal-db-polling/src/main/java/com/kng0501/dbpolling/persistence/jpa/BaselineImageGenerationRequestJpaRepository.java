package com.kng0501.dbpolling.persistence.jpa;

import com.kng0501.dbpolling.persistence.entity.BaselineImageGenerationRequestEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BaselineImageGenerationRequestJpaRepository
        extends JpaRepository<BaselineImageGenerationRequestEntity, Long> {

    @Query("select request from BaselineImageGenerationRequestEntity request order by request.id")
    List<BaselineImageGenerationRequestEntity> findOldest(Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BaselineImageGenerationRequestEntity request where request.id = :requestId")
    int deleteRequestById(@Param("requestId") long requestId);
}
