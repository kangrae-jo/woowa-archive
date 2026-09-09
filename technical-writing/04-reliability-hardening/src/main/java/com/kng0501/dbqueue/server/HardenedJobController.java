package com.kng0501.dbqueue.server;

import com.kng0501.dbqueue.application.JobQueue;
import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.JobStatus;
import com.kng0501.dbqueue.domain.Monster;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HardenedJobController {

    private final JobQueue queue;

    public HardenedJobController(final JobQueue queue) {
        this.queue = queue;
    }

    @PostMapping("/jobs")
    ResponseEntity<HardenedJobAcceptedResponse> request(@RequestBody final CreateJobRequest request) {
        final JobQueue.Registration registered = queue.request(request.requiredPrompt());
        return ResponseEntity.accepted().body(
                new HardenedJobAcceptedResponse(registered.jobId(), registered.monsterId())
        );
    }

    @GetMapping("/jobs/{jobId}")
    ResponseEntity<HardenedJobResponse> findJob(@PathVariable final long jobId) {
        return queue.findJob(jobId)
                .map(this::responseFor)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private HardenedJobResponse responseFor(final Job job) {
        final Monster monster = queue.findMonster(job.monsterId())
                .orElseThrow(() -> new IllegalStateException("결과 대상 Monster가 없습니다: job_id=" + job.jobId()));
        return new HardenedJobResponse(
                job.jobId(), job.monsterId(), job.prompt(), job.status(), job.attemptCount(),
                job.nextAttemptAt(), job.deadlineAt(), job.createdAt(), job.startedAt(),
                job.finishedAt(), job.lastError(), monster.image()
        );
    }

    public record HardenedJobAcceptedResponse(long jobId, long monsterId) {
    }

    public record HardenedJobResponse(
            long jobId,
            long monsterId,
            String prompt,
            JobStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            Instant deadlineAt,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            String lastError,
            String image
    ) {
    }
}
