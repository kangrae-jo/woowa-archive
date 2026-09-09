package com.kng0501.dbpolling.server.persistence.jpa;

import com.kng0501.dbpolling.server.persistence.entity.MonsterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonsterJpaRepository extends JpaRepository<MonsterEntity, Long> {
}
