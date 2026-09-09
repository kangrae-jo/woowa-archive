package com.kng0501.dbqueue.server.presentation;

import com.kng0501.dbqueue.server.application.JobQueryService;
import com.kng0501.dbqueue.server.application.JobRegistrationService;
import com.kng0501.dbqueue.server.domain.JobQueryResult;
import com.kng0501.dbqueue.server.domain.JobStatus;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JobController {

    private final JobRegistrationService registrationService;
    private final JobQueryService queryService;

    public JobController(
            final JobRegistrationService registrationService,
            final JobQueryService queryService
    ) {
        this.registrationService = registrationService;
        this.queryService = queryService;
    }

    @PostMapping("/jobs")
    ResponseEntity<JobAcceptedResponse> request(@RequestBody final CreateJobRequest request) {
        final JobRegistrationService.Registration registered = registrationService.request(request.requiredPrompt());
        return ResponseEntity.accepted().body(
                new JobAcceptedResponse(registered.jobId(), registered.monsterId())
        );
    }

    @GetMapping("/jobs/{jobId}")
    ResponseEntity<JobResponse> findJob(@PathVariable final long jobId) {
        return queryService.findJob(jobId)
                .map(this::responseFor)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private JobResponse responseFor(final JobQueryResult job) {
        return new JobResponse(
                job.jobId(), job.monsterId(), job.prompt(), job.status(), job.attemptCount(),
                job.nextAttemptAt(), job.deadlineAt(), job.createdAt(), job.startedAt(),
                job.finishedAt(), job.lastError(), job.image()
        );
    }

    public record JobAcceptedResponse(long jobId, long monsterId) {
    }

    public record JobResponse(
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
