package com.kng0501.dbqueue.persistence;

import com.kng0501.dbqueue.application.JobQueue;
import com.kng0501.dbqueue.domain.QueueSettings;
import com.kng0501.dbqueue.persistence.entity.ImageGenerationJobEntity;
import com.kng0501.dbqueue.persistence.jpa.ImageGenerationJobJpaRepository;
import com.kng0501.technicalwriting.time.MicrosecondClock;
import java.time.Clock;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = JobQueue.class)
@EntityScan(basePackageClasses = ImageGenerationJobEntity.class)
@EnableJpaRepositories(basePackageClasses = ImageGenerationJobJpaRepository.class)
@EnableConfigurationProperties(QueueSettings.class)
public class HardenedPersistenceConfiguration {

    @Bean
    Clock hardenedClock() {
        return new MicrosecondClock(Clock.systemUTC());
    }
}
