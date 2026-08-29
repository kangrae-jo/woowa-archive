package com.kng0501.dbpolling.application;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DbPollingScheduler implements AutoCloseable {

    private final DbPollingWorker worker;
    private final long pollingIntervalMillis;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();

    public DbPollingScheduler(DbPollingWorker worker, Duration pollingInterval) {
        long intervalMillis = pollingInterval.toMillis();
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("polling interval은 1ms 이상이어야 합니다.");
        }

        this.worker = worker;
        this.pollingIntervalMillis = intervalMillis;
        this.executor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("db-polling-worker").factory()
        );
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("DB Polling scheduler는 한 번만 시작할 수 있습니다.");
        }

        executor.scheduleWithFixedDelay(
                worker::pollOnce,
                0,
                pollingIntervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
