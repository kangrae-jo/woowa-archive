package com.kng0501.dbqueue.application;

import com.kng0501.dbqueue.domain.ImageGenerator;
import com.kng0501.dbqueue.domain.Job;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

public final class JobWorker {
    private static final Logger LOG = System.getLogger(JobWorker.class.getName());

    private final JobQueue queue;
    private final ImageGenerator generator;

    public JobWorker(JobQueue queue, ImageGenerator generator) {
        this.queue = queue;
        this.generator = generator;
    }

    public void execute(Job claim) {
        try {
            String image = generator.generate(claim.prompt());
            if (!queue.complete(claim.jobId(), claim.claimToken(), image)) {
                LOG.log(Level.WARNING, context(claim) + " cause=completion rejected (expired or stale claim)");
            }
        } catch (Exception failure) {
            recordFailure(claim, failure);
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    void recordFailure(Job claim, Exception failure) {
        LOG.log(Level.WARNING, context(claim) + " cause=" + failure, failure);
        try {
            if (!queue.fail(claim, failure)) {
                LOG.log(Level.WARNING, context(claim) + " cause=failure transition rejected (expired or stale claim)");
            }
        } catch (RuntimeException persistenceFailure) {
            // RUNNING 행과 기한을 보존한다. 별도 복구 Scheduler가 이후 다시 접근할 수 있다.
            LOG.log(Level.ERROR, context(claim) + " cause=failure persistence failed; timeout recovery required",
                    persistenceFailure);
        }
    }

    private static String context(Job claim) {
        return "job_id=" + claim.jobId() + " attempt_count=" + claim.attemptCount();
    }
}
