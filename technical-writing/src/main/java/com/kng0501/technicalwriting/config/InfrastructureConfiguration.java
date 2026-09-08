package com.kng0501.technicalwriting.config;

import com.kng0501.technicalwriting.time.MicrosecondClock;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InfrastructureConfiguration {

    @Bean
    Clock clock() {
        return new MicrosecondClock(Clock.systemUTC());
    }
}
