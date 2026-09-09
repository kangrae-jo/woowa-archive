package com.kng0501.dbqueue.worker.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class ImageResultUpdater {

    @PersistenceContext
    private EntityManager entityManager;

    public void updateImage(final long monsterId, final String image) {
        final int changed = entityManager.createNativeQuery(
                        "UPDATE queue_monster SET image = :image WHERE monster_id = :monsterId"
                )
                .setParameter("image", image)
                .setParameter("monsterId", monsterId)
                .executeUpdate();
        if (changed != 1) {
            throw new IllegalStateException("결과 대상 Monster가 없습니다: monster_id=" + monsterId);
        }
    }
}
