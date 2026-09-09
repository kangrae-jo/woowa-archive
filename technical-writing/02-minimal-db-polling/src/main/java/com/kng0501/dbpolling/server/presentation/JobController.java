package com.kng0501.dbpolling.server.presentation;

import com.kng0501.dbpolling.server.application.ImageGenerationService;
import com.kng0501.dbpolling.server.domain.Monster;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JobController {

    private final ImageGenerationService imageGenerationService;

    public JobController(final ImageGenerationService imageGenerationService) {
        this.imageGenerationService = imageGenerationService;
    }

    @PostMapping("/jobs")
    ResponseEntity<JobAcceptedResponse> request(@RequestBody final CreateJobRequest request) {
        final long monsterId = imageGenerationService.request(request.requiredPrompt());
        return ResponseEntity.accepted().body(new JobAcceptedResponse(monsterId));
    }

    @GetMapping("/monsters/{monsterId}")
    ResponseEntity<MonsterResponse> findMonster(@PathVariable final long monsterId) {
        return imageGenerationService.findMonster(monsterId)
                .map(MonsterResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record JobAcceptedResponse(long monsterId) {
    }

    public record MonsterResponse(long monsterId, String prompt, String image) {

        private static MonsterResponse from(final Monster monster) {
            return new MonsterResponse(monster.id(), monster.prompt(), monster.image());
        }
    }
}
