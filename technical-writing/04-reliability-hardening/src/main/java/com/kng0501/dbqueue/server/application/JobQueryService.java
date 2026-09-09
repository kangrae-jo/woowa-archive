package com.kng0501.dbqueue.server.application;

import com.kng0501.dbqueue.server.domain.JobQueryResult;
import com.kng0501.dbqueue.server.persistence.jpa.ImageGenerationJobJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobQueryService {

    private final ImageGenerationJobJpaRepository jobs;

    public JobQueryService(final ImageGenerationJobJpaRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public Optional<JobQueryResult> findJob(final long jobId) {
        return jobs.findById(jobId).map(job -> new JobQueryResult(
                job.getId(), job.getMonster().getId(), job.getPrompt(), job.getStatus(), job.getAttemptCount(),
                job.getNextAttemptAt(), job.getDeadlineAt(), job.getCreatedAt(), job.getStartedAt(),
                job.getFinishedAt(), job.getLastError(), job.getMonster().getImage()
        ));
    }
}
