package com.kng0501.dbpolling.worker.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ImageResultUpdater {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateImage(final long monsterId, final String image) {
        entityManager.createNativeQuery("UPDATE monster SET image = :image WHERE id = :monsterId")
                .setParameter("image", image)
                .setParameter("monsterId", monsterId)
                .executeUpdate();
    }
}
