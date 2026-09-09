package com.kng0501.dbqueue.server.persistence.jpa;

import com.kng0501.dbqueue.server.persistence.entity.MonsterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonsterJpaRepository extends JpaRepository<MonsterEntity, Long> {
}
