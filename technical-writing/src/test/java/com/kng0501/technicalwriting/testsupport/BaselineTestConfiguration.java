package com.kng0501.technicalwriting.testsupport;

import com.kng0501.dbpolling.domain.ImageGenerator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class BaselineTestConfiguration {

    @Bean
    @Primary
    ImageGenerator baselineTestImageGenerator(final BaselineTestImageGenerator generator) {
        return generator;
    }

    @Bean
    BaselineTestImageGenerator baselineTestImageGeneratorController() {
        return new BaselineTestImageGenerator();
    }
}
