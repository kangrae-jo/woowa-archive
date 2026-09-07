package com.kng0501.dbqueue.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableClock extends Clock {
    private final AtomicReference<Instant> time;
    private final ZoneId zone;

    public MutableClock() {
        this(new AtomicReference<>(Instant.parse("2026-09-06T00:00:00Z")), ZoneOffset.UTC);
    }

    private MutableClock(final AtomicReference<Instant> time, final ZoneId zone) {
        this.time = time;
        this.zone = zone;
    }

    public void advance(final Duration duration) {
        time.updateAndGet(now -> now.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(final ZoneId zone) {
        return new MutableClock(time, zone);
    }

    @Override
    public Instant instant() {
        return time.get();
    }
}
