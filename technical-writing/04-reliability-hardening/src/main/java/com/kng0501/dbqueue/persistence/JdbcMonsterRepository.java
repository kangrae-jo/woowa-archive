package com.kng0501.dbqueue.persistence;

import com.kng0501.dbqueue.domain.Monster;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcMonsterRepository {
    private final JdbcTemplate jdbc;

    public JdbcMonsterRepository(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long save(final String prompt) {
        final var key = new GeneratedKeyHolder();
        final int count = jdbc.update(connection -> {
            final var statement = connection.prepareStatement(
                    "INSERT INTO queue_monster(prompt) VALUES (?)", new String[]{"monster_id"}
            );
            statement.setString(1, prompt);
            return statement;
        }, key);
        if (count != 1 || key.getKey() == null) {
            throw new IllegalStateException("Monster 저장 실패");
        }
        return key.getKey().longValue();
    }

    public int updateImage(final long monsterId, final String image) {
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("생성 이미지는 비어 있을 수 없습니다.");
        }
        return jdbc.update("UPDATE queue_monster SET image = ? WHERE monster_id = ?", image, monsterId);
    }

    public Optional<Monster> findById(final long monsterId) {
        return jdbc.query(
                "SELECT monster_id, prompt, image FROM queue_monster WHERE monster_id = ?",
                (rs, row) -> new Monster(rs.getLong("monster_id"), rs.getString("prompt"), rs.getString("image")),
                monsterId
        ).stream().findFirst();
    }
}
