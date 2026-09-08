package com.kng0501.technicalwriting.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class MicrosecondClock extends Clock {

    private final Clock delegate;

    public MicrosecondClock(final Clock delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public ZoneId getZone() {
        return delegate.getZone();
    }

    @Override
    public Clock withZone(final ZoneId zone) {
        return new MicrosecondClock(delegate.withZone(zone));
    }

    @Override
    public Instant instant() {
        return delegate.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
