package com.kng0501.dbpolling.worker.application;

import com.kng0501.dbpolling.worker.domain.ImageGenerationRequest;
import com.kng0501.dbpolling.worker.domain.ImageGenerator;
import com.kng0501.dbpolling.worker.persistence.ImageResultUpdater;
import com.kng0501.dbpolling.worker.persistence.entity.ImageGenerationRequestEntity;
import com.kng0501.dbpolling.worker.persistence.jpa.ImageGenerationRequestJpaRepository;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;

public class DbPollingWorker {

    private final ImageGenerationRequestJpaRepository requests;
    private final ImageResultUpdater results;
    private final ImageGenerator imageGenerator;
    private final Runnable afterSelection;

    public DbPollingWorker(
            final ImageGenerationRequestJpaRepository requests,
            final ImageResultUpdater results,
            final ImageGenerator imageGenerator
    ) {
        this(requests, results, imageGenerator, () -> { });
    }

    public DbPollingWorker(
            final ImageGenerationRequestJpaRepository requests,
            final ImageResultUpdater results,
            final ImageGenerator imageGenerator,
            final Runnable afterSelection
    ) {
        this.requests = requests;
        this.results = results;
        this.imageGenerator = imageGenerator;
        this.afterSelection = afterSelection;
    }

    public boolean pollOnce() {
        final Optional<ImageGenerationRequest> request = requests.findOldest(PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(entity -> new ImageGenerationRequest(entity.getId(), entity.getPrompt()));
        if (request.isEmpty()) {
            return false;
        }

        final ImageGenerationRequest target = request.get();
        afterSelection.run();
        requests.deleteRequestById(target.id());

        final String image = imageGenerator.generate(target.prompt());
        results.updateImage(target.id(), image);
        return true;
    }
}
