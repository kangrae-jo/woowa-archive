package com.kng0501.measurement;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class QueueDepthMonitor implements AutoCloseable {

    private final MeasurementDatabase database;
    private final Duration interval;
    private final ScheduledExecutorService executor;
    private final AtomicInteger maximumPendingCount = new AtomicInteger();
    private final AtomicReference<RuntimeException> failure = new AtomicReference<>();

    public QueueDepthMonitor(final MeasurementDatabase database, final Duration interval) {
        this.database = database;
        this.interval = interval;
        this.executor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("measurement-queue-depth-", 0).factory()
        );
    }

    public void start(final String runId) {
        updateMaximum(runId);
        executor.scheduleWithFixedDelay(
                () -> observe(runId),
                interval.toNanos(),
                interval.toNanos(),
                TimeUnit.NANOSECONDS
        );
    }

    public int maximumPendingCount() {
        final RuntimeException observedFailure = failure.get();
        if (observedFailure != null) {
            throw new IllegalStateException("대기열 깊이 관찰이 실패했습니다.", observedFailure);
        }
        return maximumPendingCount.get();
    }

    private void observe(final String runId) {
        try {
            updateMaximum(runId);
        } catch (final RuntimeException exception) {
            failure.compareAndSet(null, exception);
        }
    }

    private void updateMaximum(final String runId) {
        final int pendingCount = (int) database.findJobs(runId).stream()
                .filter(job -> job.status() == JobStatus.PENDING)
                .count();
        maximumPendingCount.accumulateAndGet(pendingCount, Math::max);
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("대기열 깊이 관찰 Executor가 종료되지 않았습니다.");
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("대기열 깊이 관찰 Executor 종료가 중단됐습니다.", exception);
        }
    }
}
