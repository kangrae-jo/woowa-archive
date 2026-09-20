package com.kng0501.measurement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

final class MeasurementPropertiesTest {

    @Test
    void 기본_측정_설정은_유효하다() {
        final MeasurementProperties properties = new MeasurementProperties();

        assertDoesNotThrow(properties::validate);
    }

    @Test
    void 요청_개수와_동시성_제약을_검증한다() {
        final MeasurementProperties zeroJobs = new MeasurementProperties();
        zeroJobs.setJobCount(0);
        assertThrows(IllegalArgumentException.class, zeroJobs::validate);

        final MeasurementProperties zeroConcurrency = new MeasurementProperties();
        zeroConcurrency.setRequestConcurrency(0);
        assertThrows(IllegalArgumentException.class, zeroConcurrency::validate);

        final MeasurementProperties tooManyRequests = new MeasurementProperties();
        tooManyRequests.setJobCount(3);
        tooManyRequests.setRequestConcurrency(4);
        assertThrows(IllegalArgumentException.class, tooManyRequests::validate);
    }

    @Test
    void 시간_설정과_인증정보가_포함된_baseUrl을_거부한다() {
        final MeasurementProperties zeroTimeout = new MeasurementProperties();
        zeroTimeout.setTimeout(Duration.ZERO);
        assertThrows(IllegalArgumentException.class, zeroTimeout::validate);

        final MeasurementProperties zeroPollingInterval = new MeasurementProperties();
        zeroPollingInterval.setPollingInterval(Duration.ZERO);
        assertThrows(IllegalArgumentException.class, zeroPollingInterval::validate);

        final MeasurementProperties credentialUrl = new MeasurementProperties();
        credentialUrl.setBaseUrl("http://user:password@127.0.0.1:8080");
        assertThrows(IllegalArgumentException.class, credentialUrl::validate);
    }
}
