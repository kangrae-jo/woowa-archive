package com.kng0501.technicalwriting.testsupport;

import com.kng0501.dbqueue.domain.ImageGenerator;
import com.kng0501.dbqueue.support.MutableClock;
import java.time.Clock;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class HardenedTestConfiguration {

    @Bean
    @Primary
    Clock mutableClock(final MutableClock clock) {
        return clock;
    }

    @Bean
    MutableClock mutableClockController() {
        return new MutableClock();
    }

    @Bean
    @Primary
    ImageGenerator hardenedTestImageGenerator(final HardenedTestImageGenerator generator) {
        return generator;
    }

    @Bean
    HardenedTestImageGenerator hardenedTestImageGeneratorController() {
        return new HardenedTestImageGenerator();
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
