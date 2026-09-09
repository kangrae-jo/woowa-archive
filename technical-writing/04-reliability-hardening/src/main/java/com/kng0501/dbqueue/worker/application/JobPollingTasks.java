package com.kng0501.dbqueue.worker.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class JobPollingTasks {

    private final ExpiredJobRecovery recovery;
    private final JobScheduler scheduler;

    public JobPollingTasks(final ExpiredJobRecovery recovery, final JobScheduler scheduler) {
        this.recovery = recovery;
        this.scheduler = scheduler;
    }

    @Scheduled(fixedDelayString = "${db-queue.polling-interval}")
    public void dispatch() {
        runSafely("dispatch", scheduler::dispatch);
    }

    @Scheduled(fixedDelayString = "${db-queue.recovery-interval}")
    public void recoverExpired() {
        runSafely("recovery", recovery::recoverExpired);
    }

    private void runSafely(final String operation, final Runnable action) {
        try {
            action.run();
        } catch (final RuntimeException failure) {
            log.error("operation={} job_id=unassigned attempt_count=unknown cause={}",
                    operation, failure.toString(), failure);
        }
    }
}
