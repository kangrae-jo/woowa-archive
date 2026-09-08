package com.kng0501.dbpolling.persistence.jpa;

import com.kng0501.dbpolling.persistence.entity.BaselineMonsterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BaselineMonsterJpaRepository extends JpaRepository<BaselineMonsterEntity, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update BaselineMonsterEntity monster set monster.image = :image where monster.id = :monsterId")
    int updateImage(@Param("monsterId") long monsterId, @Param("image") String image);
}
