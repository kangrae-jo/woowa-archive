package com.kng0501.dbpolling.application;

import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;

public final class ImageGenerationService {

    private final MonsterRepository monsterRepository;
    private final ImageGenerationRequestRepository requestRepository;

    public ImageGenerationService(
            MonsterRepository monsterRepository,
            ImageGenerationRequestRepository requestRepository
    ) {
        this.monsterRepository = monsterRepository;
        this.requestRepository = requestRepository;
    }

    public long request(String prompt) {
        long monsterId = monsterRepository.save(prompt);
        requestRepository.enqueue(prompt);
        return monsterId;
    }
}
