package com.kng0501.measurement;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class PercentilesTest {

    @Test
    void nearestRank_방식으로_p50_p95_p99을_계산한다() {
        final List<Duration> durations = IntStream.rangeClosed(1, 100)
                .mapToObj(Duration::ofMillis)
                .toList();

        final LatencyPercentiles values = Percentiles.calculate(durations);

        assertAll(
                () -> assertEquals(50L, values.p50Millis()),
                () -> assertEquals(95L, values.p95Millis()),
                () -> assertEquals(99L, values.p99Millis())
        );
    }

    @Test
    void 표본이_없으면_percentile은_null이다() {
        final LatencyPercentiles values = Percentiles.calculate(List.of());

        assertAll(
                () -> assertNull(values.p50Millis()),
                () -> assertNull(values.p95Millis()),
                () -> assertNull(values.p99Millis())
        );
    }
}
