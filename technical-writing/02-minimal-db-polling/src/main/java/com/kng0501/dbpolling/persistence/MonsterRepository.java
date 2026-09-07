package com.kng0501.dbpolling.persistence;

import com.kng0501.dbpolling.domain.Monster;
import java.util.Optional;

public interface MonsterRepository {

    long save(final String prompt);

    Optional<Monster> findById(final long id);

    void updateImage(final long monsterId, final String image);
}
