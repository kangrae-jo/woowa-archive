package com.kng0501.dbpolling.server;

import com.kng0501.dbpolling.application.ImageGenerationService;
import com.kng0501.dbpolling.domain.Monster;
import com.kng0501.dbpolling.persistence.MonsterRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BaselineJobController {

    private final ImageGenerationService imageGenerationService;
    private final MonsterRepository monsters;

    public BaselineJobController(
            final ImageGenerationService imageGenerationService,
            final MonsterRepository monsters
    ) {
        this.imageGenerationService = imageGenerationService;
        this.monsters = monsters;
    }

    @PostMapping("/jobs")
    ResponseEntity<BaselineJobAcceptedResponse> request(@RequestBody final CreateJobRequest request) {
        final long monsterId = imageGenerationService.request(request.requiredPrompt());
        return ResponseEntity.accepted().body(new BaselineJobAcceptedResponse(monsterId));
    }

    @GetMapping("/monsters/{monsterId}")
    ResponseEntity<BaselineMonsterResponse> findMonster(@PathVariable final long monsterId) {
        return monsters.findById(monsterId)
                .map(BaselineMonsterResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record BaselineJobAcceptedResponse(long monsterId) {
    }

    public record BaselineMonsterResponse(long monsterId, String prompt, String image) {

        private static BaselineMonsterResponse from(final Monster monster) {
            return new BaselineMonsterResponse(monster.id(), monster.prompt(), monster.image());
        }
    }
}
