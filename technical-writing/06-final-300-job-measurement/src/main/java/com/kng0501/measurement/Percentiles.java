package com.kng0501.measurement;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

public final class Percentiles {

    private Percentiles() {
    }

    public static LatencyPercentiles calculate(final List<Duration> durations) {
        if (durations.isEmpty()) {
            return new LatencyPercentiles(null, null, null);
        }

        final List<Long> sortedMillis = durations.stream()
                .map(Duration::toMillis)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new LatencyPercentiles(
                nearestRank(sortedMillis, 0.50),
                nearestRank(sortedMillis, 0.95),
                nearestRank(sortedMillis, 0.99)
        );
    }

    private static long nearestRank(final List<Long> sortedMillis, final double percentile) {
        final int index = Math.max(0, (int) Math.ceil(percentile * sortedMillis.size()) - 1);
        return sortedMillis.get(index);
    }
}
