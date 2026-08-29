package com.kng0501.dbpolling.persistence;

import com.kng0501.dbpolling.domain.ImageGenerationRequest;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

public final class JdbcImageGenerationRequestRepository implements ImageGenerationRequestRepository {

    private static final String INSERT_SQL = "INSERT INTO image_generation_request(prompt) VALUES (?)";
    private static final String FIND_OLDEST_SQL = """
            SELECT id, prompt
            FROM image_generation_request
            ORDER BY id
            LIMIT 1
            """;
    private static final String DELETE_SQL = "DELETE FROM image_generation_request WHERE id = ?";
    private static final String COUNT_SQL = "SELECT COUNT(*) FROM image_generation_request";

    private final JdbcTemplate jdbcTemplate;

    public JdbcImageGenerationRequestRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public long enqueue(String prompt) {
        validatePrompt(prompt);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        int updatedRows = jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(INSERT_SQL, new String[]{"id"});
            statement.setString(1, prompt);
            return statement;
        }, keyHolder);

        if (updatedRows != 1 || keyHolder.getKey() == null) {
            throw new IllegalStateException("image generation request 저장에 실패했습니다.");
        }
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<ImageGenerationRequest> findOldest() {
        List<ImageGenerationRequest> requests = jdbcTemplate.query(
                FIND_OLDEST_SQL,
                (resultSet, rowNumber) -> new ImageGenerationRequest(
                        resultSet.getLong("id"),
                        resultSet.getString("prompt")
                )
        );
        return requests.stream().findFirst();
    }

    @Override
    public void deleteById(long requestId) {
        jdbcTemplate.update(DELETE_SQL, requestId);
    }

    @Override
    public long count() {
        Long count = jdbcTemplate.queryForObject(COUNT_SQL, Long.class);
        if (count == null) {
            throw new IllegalStateException("image generation request 개수를 조회할 수 없습니다.");
        }
        return count;
    }

    private static void validatePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt는 비어 있을 수 없습니다.");
        }
    }
}
