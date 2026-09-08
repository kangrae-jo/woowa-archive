package com.kng0501.dbqueue.application;

import com.kng0501.dbqueue.domain.ImageGenerator;
import com.kng0501.dbqueue.domain.Job;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobWorker {
    private final JobQueue queue;
    private final ImageGenerator generator;

    public JobWorker(final JobQueue queue, final ImageGenerator generator) {
        this.queue = queue;
        this.generator = generator;
    }

    public void execute(final Job claim) {
        try {
            final String image = generator.generate(claim.prompt());
            if (!queue.complete(claim.jobId(), claim.claimToken(), image)) {
                log.warn("job_id={} attempt_count={} cause=completion rejected (expired or stale claim)",
                        claim.jobId(), claim.attemptCount());
            }
        } catch (final Exception failure) {
            recordFailure(claim, failure);
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    void recordFailure(final Job claim, final Exception failure) {
        log.warn("job_id={} attempt_count={} cause={}",
                claim.jobId(), claim.attemptCount(), failure.toString(), failure);
        try {
            if (!queue.fail(claim, failure)) {
                log.warn("job_id={} attempt_count={} cause=failure transition rejected (expired or stale claim)",
                        claim.jobId(), claim.attemptCount());
            }
        } catch (final RuntimeException persistenceFailure) {
            // RUNNING 행과 기한을 보존한다. 별도 복구 Scheduler가 이후 다시 접근할 수 있다.
            log.error("job_id={} attempt_count={} cause=failure persistence failed; timeout recovery required",
                    claim.jobId(), claim.attemptCount(), persistenceFailure);
        }
    }
}
