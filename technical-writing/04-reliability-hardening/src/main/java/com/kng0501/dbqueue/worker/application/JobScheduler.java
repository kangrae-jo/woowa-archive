package com.kng0501.dbqueue.worker.application;

import com.kng0501.dbqueue.worker.domain.Job;
import com.kng0501.dbqueue.worker.domain.QueueSettings;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

@Slf4j
public class JobScheduler implements AutoCloseable {

    private final JobQueue queue;
    private final JobWorker worker;
    private final QueueSettings settings;
    private final ThreadPoolExecutor executions;
    private final Semaphore slots;
    private boolean closed;

    public JobScheduler(
            final JobQueue queue,
            final JobWorker worker,
            final QueueSettings settings,
            @Qualifier("jobExecutionExecutor") final ThreadPoolExecutor executions
    ) {
        this.queue = queue;
        this.worker = worker;
        this.settings = settings;
        this.executions = executions;
        this.slots = new Semaphore(settings.concurrency());
    }

    public synchronized void dispatch() {
        if (closed) {
            return;
        }
        for (int offered = 0; offered < settings.concurrency(); offered++) {
            if (!slots.tryAcquire()) {
                return;
            }
            Job claim = null;
            boolean submitted = false;
            try {
                final Optional<Long> candidate = queue.findCandidate();
                if (candidate.isEmpty()) {
                    return;
                }
                final Optional<Job> selected = queue.tryClaim(candidate.get());
                if (selected.isEmpty()) {
                    return;
                }
                claim = selected.get();
                executions.execute(new ClaimedTask(claim));
                submitted = true;
            } catch (final RuntimeException failure) {
                if (claim == null) {
                    throw failure;
                }
                worker.recordFailure(claim, failure);
                return;
            } finally {
                if (!submitted) {
                    slots.release();
                }
            }
        }
    }

    @PreDestroy
    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        for (final Runnable task : executions.shutdownNow()) {
            ((ClaimedTask) task).cancelBeforeStart();
        }
        awaitTermination(executions);
    }

    public boolean isTerminated() {
        return executions.isTerminated();
    }

    private static void awaitTermination(final ExecutorService executor) {
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Executor가 종료되지 않았습니다. Generator의 interrupt 협조가 필요합니다.");
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("Executor 종료 대기가 중단됐습니다.", interrupted);
        }
    }

    private final class ClaimedTask implements Runnable {

        private final Job claim;
        private final AtomicBoolean accepted = new AtomicBoolean();

        private ClaimedTask(final Job claim) {
            this.claim = claim;
        }

        @Override
        public void run() {
            if (!accepted.compareAndSet(false, true)) {
                return;
            }
            try {
                worker.execute(claim);
            } finally {
                slots.release();
            }
        }

        private void cancelBeforeStart() {
            if (accepted.compareAndSet(false, true)) {
                try {
                    worker.recordFailure(claim, new IllegalStateException("Scheduler closed before execution"));
                } finally {
                    slots.release();
                }
            }
        }
    }
}
