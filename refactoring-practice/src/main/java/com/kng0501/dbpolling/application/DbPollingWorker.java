package com.kng0501.dbpolling.application;

import com.kng0501.dbpolling.domain.ImageGenerationRequest;
import com.kng0501.dbpolling.domain.ImageGenerator;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;
import java.util.Optional;

public final class DbPollingWorker {

    private final ImageGenerationRequestRepository requestRepository;
    private final MonsterRepository monsterRepository;
    private final ImageGenerator imageGenerator;

    public DbPollingWorker(
            ImageGenerationRequestRepository requestRepository,
            MonsterRepository monsterRepository,
            ImageGenerator imageGenerator
    ) {
        this.requestRepository = requestRepository;
        this.monsterRepository = monsterRepository;
        this.imageGenerator = imageGenerator;
    }

    public boolean pollOnce() {
        Optional<ImageGenerationRequest> request = requestRepository.findOldest();
        if (request.isEmpty()) {
            return false;
        }

        ImageGenerationRequest target = request.get();
        requestRepository.deleteById(target.id());

        String image = imageGenerator.generate(target.prompt());
        monsterRepository.updateImage(target.id(), image);
        return true;
    }
}
