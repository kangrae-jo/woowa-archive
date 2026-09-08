package com.kng0501.dbqueue.persistence.jpa;

import com.kng0501.dbqueue.persistence.entity.QueueMonsterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueMonsterJpaRepository extends JpaRepository<QueueMonsterEntity, Long> {
}
