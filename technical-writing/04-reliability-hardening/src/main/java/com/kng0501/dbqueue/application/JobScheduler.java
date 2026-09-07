package com.kng0501.dbqueue.application;

import com.kng0501.dbqueue.domain.ImageGenerator;
import com.kng0501.dbqueue.domain.Job;
import com.kng0501.dbqueue.domain.QueueSettings;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JobScheduler implements AutoCloseable {
    private static final Logger LOG = System.getLogger(JobScheduler.class.getName());

    private final JobQueue queue;
    private final JobWorker worker;
    private final QueueSettings settings;
    private final ThreadPoolExecutor executions;
    private final ScheduledExecutorService control;
    private final Semaphore slots;
    private boolean started;
    private boolean closed;

    public JobScheduler(final JobQueue queue, final ImageGenerator generator, final QueueSettings settings) {
        this(queue, generator, settings, new ThreadPoolExecutor(
                settings.concurrency(), settings.concurrency(), 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(settings.concurrency()),
                Thread.ofPlatform().name("db-job-execution-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        ));
    }

    // 작업 제출 거부를 결정적으로 검증하기 위한 실행기 주입 지점.
    JobScheduler(final JobQueue queue, final ImageGenerator generator, final QueueSettings settings, final ThreadPoolExecutor executions) {
        this.queue = queue;
        this.worker = new JobWorker(queue, generator);
        this.settings = settings;
        this.executions = executions;
        this.slots = new Semaphore(settings.concurrency());
        this.control = Executors.newScheduledThreadPool(
                2, Thread.ofPlatform().name("db-job-control-", 0).factory()
        );
    }

    public synchronized void start() {
        if (started || closed) {
            throw new IllegalStateException("Scheduler는 열린 상태에서 한 번만 시작할 수 있습니다.");
        }
        started = true;
        control.scheduleWithFixedDelay(
                () -> runSafely("dispatch", this::dispatch),
                0, settings.pollingInterval().toMillis(), TimeUnit.MILLISECONDS
        );
        control.scheduleWithFixedDelay(
                () -> runSafely("recovery", queue::recoverExpired),
                0, settings.recoveryInterval().toMillis(), TimeUnit.MILLISECONDS
        );
    }

    synchronized void dispatch() {
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
                    return; // 경합에서 지면 다음 Polling까지 기다린다.
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

    private void runSafely(final String operation, final Runnable action) {
        try {
            action.run();
        } catch (final RuntimeException failure) {
            LOG.log(Level.ERROR, "operation=" + operation
                    + " job_id=unassigned attempt_count=unknown cause=" + failure, failure);
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        control.shutdownNow();
        for (final Runnable task : executions.shutdownNow()) {
            ((ClaimedTask) task).cancelBeforeStart();
        }
        awaitTermination(control);
        awaitTermination(executions);
    }

    boolean isTerminated() {
        return control.isTerminated() && executions.isTerminated();
    }

    private static void awaitTermination(final ExecutorService executor) {
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.log(Level.WARNING, "Executor가 종료되지 않았습니다. Generator의 interrupt 협조가 필요합니다.");
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "Executor 종료 대기가 중단됐습니다.", interrupted);
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
