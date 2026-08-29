package com.kng0501.dbpolling.failure;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kng0501.dbpolling.application.ImageGenerationService;
import com.kng0501.dbpolling.domain.ImageGenerationRequest;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.JdbcImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.JdbcMonsterRepository;
import com.kng0501.dbpolling.support.TestDatabase;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("failure-reproduction")
class RequestRegistrationAtomicityFailureTest {

    private DataSource dataSource;
    private ImageGenerationRequestRepository requestRepository;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabase.createInitializedDataSource();
        requestRepository = new JdbcImageGenerationRequestRepository(dataSource);
    }

    @Test
    void monster와_이미지_생성_job은_함께_저장되거나_함께_저장되지_않는다() {
        var service = new ImageGenerationService(
                new JdbcMonsterRepository(dataSource),
                new EnqueueFailingRequestRepository(requestRepository)
        );

        assertThrows(SimulatedEnqueueFailureException.class, () -> service.request("blue dragon"));

        Integer monsterCount = new JdbcTemplate(dataSource)
                .queryForObject("SELECT COUNT(*) FROM monster", Integer.class);
        assertAll(
                () -> assertEquals(
                        0,
                        monsterCount,
                        "요청 등록이 실패하면 먼저 저장한 monster도 남지 않아야 합니다."
                ),
                () -> assertEquals(
                        0,
                        requestRepository.count(),
                        "요청 등록 실패 후 image generation job이 남지 않아야 합니다."
                )
        );
    }

    private static final class EnqueueFailingRequestRepository
            implements ImageGenerationRequestRepository {

        private final ImageGenerationRequestRepository delegate;

        private EnqueueFailingRequestRepository(ImageGenerationRequestRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public long enqueue(String prompt) {
            throw new SimulatedEnqueueFailureException();
        }

        @Override
        public Optional<ImageGenerationRequest> findOldest() {
            return delegate.findOldest();
        }

        @Override
        public void deleteById(long requestId) {
            delegate.deleteById(requestId);
        }

        @Override
        public long count() {
            return delegate.count();
        }
    }

    private static final class SimulatedEnqueueFailureException extends RuntimeException {
    }
}
