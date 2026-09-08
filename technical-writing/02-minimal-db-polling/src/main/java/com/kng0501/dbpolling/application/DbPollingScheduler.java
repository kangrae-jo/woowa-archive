package com.kng0501.dbpolling.application;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class DbPollingScheduler implements SmartLifecycle, AutoCloseable {

    private final DbPollingWorker worker;
    private final long pollingIntervalMillis;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private ScheduledExecutorService executor;

    public DbPollingScheduler(final DbPollingWorker worker, final BaselineSettings settings) {
        final Duration pollingInterval = settings.pollingInterval();
        final long intervalMillis = pollingInterval.toMillis();
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("polling interval은 1ms 이상이어야 합니다.");
        }

        this.worker = worker;
        this.pollingIntervalMillis = intervalMillis;
        this.autoStartup = settings.schedulingEnabled();
    }

    private final boolean autoStartup;

    @Override
    public synchronized void start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("DB Polling scheduler는 한 번만 시작할 수 있습니다.");
        }

        executor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("db-polling-worker").factory()
        );
        running.set(true);
        executor.scheduleWithFixedDelay(
                worker::pollOnce,
                0,
                pollingIntervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return autoStartup;
    }

    @Override
    public void close() {
        stop();
    }
}
