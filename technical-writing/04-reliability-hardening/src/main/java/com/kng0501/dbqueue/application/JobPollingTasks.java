package com.kng0501.dbqueue.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "db-queue", name = "scheduling-enabled", havingValue = "true")
public class JobPollingTasks {

    private final JobQueue queue;
    private final JobScheduler scheduler;

    public JobPollingTasks(final JobQueue queue, final JobScheduler scheduler) {
        this.queue = queue;
        this.scheduler = scheduler;
    }

    @Scheduled(fixedDelayString = "${db-queue.polling-interval}")
    public void dispatch() {
        runSafely("dispatch", scheduler::dispatch);
    }

    @Scheduled(fixedDelayString = "${db-queue.recovery-interval}")
    public void recoverExpired() {
        runSafely("recovery", queue::recoverExpired);
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
