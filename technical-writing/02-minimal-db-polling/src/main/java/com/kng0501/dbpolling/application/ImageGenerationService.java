package com.kng0501.dbpolling.application;

import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;
import org.springframework.stereotype.Service;

@Service
public class ImageGenerationService {

    private final MonsterRepository monsterRepository;
    private final ImageGenerationRequestRepository requestRepository;

    public ImageGenerationService(
            final MonsterRepository monsterRepository,
            final ImageGenerationRequestRepository requestRepository
    ) {
        this.monsterRepository = monsterRepository;
        this.requestRepository = requestRepository;
    }

    public long request(final String prompt) {
        final long monsterId = monsterRepository.save(prompt);
        requestRepository.enqueue(prompt);
        return monsterId;
    }
}
