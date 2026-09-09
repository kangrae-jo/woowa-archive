package com.kng0501.dbqueue.worker;

import com.kng0501.dbqueue.worker.domain.ImageGenerator;
import com.kng0501.technicalwriting.testsupport.SqlCaptureInspector;
import java.time.Clock;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class WorkerTestConfiguration {

    @Bean
    @Primary
    Clock testClock(final TestClock clock) {
        return clock;
    }

    @Bean
    TestClock testClockController() {
        return new TestClock();
    }

    @Bean
    @Primary
    ImageGenerator testImageGenerator(final TestImageGenerator generator) {
        return generator;
    }

    @Bean
    TestImageGenerator testImageGeneratorController() {
        return new TestImageGenerator();
    }

    @Bean
    SqlCaptureInspector sqlCaptureInspector() {
        return new SqlCaptureInspector();
    }

    @Bean
    HibernatePropertiesCustomizer statementInspectorCustomizer(final SqlCaptureInspector inspector) {
        return properties -> properties.put("hibernate.session_factory.statement_inspector", inspector);
    }
}
