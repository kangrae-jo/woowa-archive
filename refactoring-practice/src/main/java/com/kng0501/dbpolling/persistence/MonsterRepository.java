package com.kng0501.dbpolling.persistence;

import com.kng0501.dbpolling.domain.Monster;
import java.util.Optional;

public interface MonsterRepository {

    long save(String prompt);

    Optional<Monster> findById(long id);

    void updateImage(long monsterId, String image);
}
