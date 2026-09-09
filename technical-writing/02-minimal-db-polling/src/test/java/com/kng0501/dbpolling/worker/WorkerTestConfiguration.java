package com.kng0501.dbpolling.worker;

import com.kng0501.dbpolling.worker.domain.ImageGenerator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class WorkerTestConfiguration {

    @Bean
    @Primary
    ImageGenerator testImageGenerator(final TestImageGenerator generator) {
        return generator;
    }

    @Bean
    TestImageGenerator testImageGeneratorController() {
        return new TestImageGenerator();
    }
}
