package com.kng0501.measurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
final class MeasurementRunnerTest {

    @Test
    void 이전_응답을_기다리지_않고_정해진_시각에_다음_요청을_전송한다() {
        final MeasurementProperties properties = properties(Duration.ofMillis(200));
        final HttpJobClient client = mock(HttpJobClient.class);
        final CountDownLatch secondStarted = new CountDownLatch(1);
        final AtomicLong secondStartedNanos = new AtomicLong();
        when(client.register(anyInt(), anyString())).thenAnswer(invocation -> {
            final int requestNumber = invocation.getArgument(0);
            if (requestNumber == 1) {
                assertTrue(secondStarted.await(3, TimeUnit.SECONDS));
            } else {
                secondStartedNanos.set(System.nanoTime());
                secondStarted.countDown();
            }
            return accepted(requestNumber, invocation.getArgument(1));
        });
        final MeasurementRunner runner = new MeasurementRunner(properties, null, client, null, null, Clock.systemUTC());

        final long startedNanos = System.nanoTime();
        final List<RequestRecord> requests = runner.registerJobs("paced", Instant.now().plusSeconds(4));

        assertEquals(2, requests.size());
        assertTrue(requests.stream().allMatch(RequestRecord::hasAcceptedJob));
        assertTrue(secondStartedNanos.get() - startedNanos >= Duration.ofMillis(150).toNanos());
    }

    @Test
    void 전체_제한_시간_뒤의_예정_요청은_전송하지_않고_실패로_기록한다() {
        final MeasurementProperties properties = properties(Duration.ofSeconds(1));
        final HttpJobClient client = mock(HttpJobClient.class);
        when(client.register(anyInt(), anyString())).thenAnswer(invocation ->
                accepted(invocation.getArgument(0), invocation.getArgument(1)));
        final MeasurementRunner runner = new MeasurementRunner(properties, null, client, null, null, Clock.systemUTC());

        final List<RequestRecord> requests = runner.registerJobs("deadline", Instant.now().plusMillis(200));

        assertEquals(2, requests.size());
        assertTrue(requests.getFirst().hasAcceptedJob());
        assertEquals("measurement timeout", requests.getLast().failureReason());
    }

    private static MeasurementProperties properties(final Duration interval) {
        final MeasurementProperties properties = new MeasurementProperties();
        properties.setJobCount(2);
        properties.setRequestConcurrency(2);
        properties.setRequestInterval(interval);
        return properties;
    }

    private static RequestRecord accepted(final int requestNumber, final String prompt) {
        final Instant now = Instant.now();
        return new RequestRecord(requestNumber, prompt, 202, (long) requestNumber, (long) requestNumber, now, now, 0, null);
    }
}
