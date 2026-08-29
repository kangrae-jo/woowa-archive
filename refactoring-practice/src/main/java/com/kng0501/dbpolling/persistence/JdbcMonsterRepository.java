package com.kng0501.dbpolling.persistence;

import com.kng0501.dbpolling.domain.Monster;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

public final class JdbcMonsterRepository implements MonsterRepository {

    private static final String INSERT_SQL = "INSERT INTO monster(prompt) VALUES (?)";
    private static final String FIND_BY_ID_SQL = "SELECT id, prompt, image FROM monster WHERE id = ?";
    private static final String UPDATE_IMAGE_SQL = "UPDATE monster SET image = ? WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;

    public JdbcMonsterRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public long save(String prompt) {
        validateText(prompt, "prompt");

        KeyHolder keyHolder = new GeneratedKeyHolder();
        int updatedRows = jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(INSERT_SQL, new String[]{"id"});
            statement.setString(1, prompt);
            return statement;
        }, keyHolder);

        if (updatedRows != 1 || keyHolder.getKey() == null) {
            throw new IllegalStateException("monster 저장에 실패했습니다.");
        }
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<Monster> findById(long id) {
        List<Monster> monsters = jdbcTemplate.query(
                FIND_BY_ID_SQL,
                (resultSet, rowNumber) -> new Monster(
                        resultSet.getLong("id"),
                        resultSet.getString("prompt"),
                        resultSet.getString("image")
                ),
                id
        );
        return monsters.stream().findFirst();
    }

    @Override
    public void updateImage(long monsterId, String image) {
        validateText(image, "image");
        jdbcTemplate.update(UPDATE_IMAGE_SQL, image, monsterId);
    }

    private static void validateText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다.");
        }
    }
}
