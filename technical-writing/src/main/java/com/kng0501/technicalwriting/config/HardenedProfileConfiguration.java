package com.kng0501.technicalwriting.config;

import com.kng0501.dbqueue.domain.ImageGenerator;
import com.kng0501.dbqueue.domain.QueueSettings;
import com.kng0501.dbqueue.persistence.entity.ImageGenerationJobEntity;
import com.kng0501.dbqueue.persistence.jpa.ImageGenerationJobJpaRepository;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@Profile("hardened")
@ComponentScan(basePackages = "com.kng0501.dbqueue.application")
@EntityScan(basePackageClasses = ImageGenerationJobEntity.class)
@EnableJpaRepositories(basePackageClasses = ImageGenerationJobJpaRepository.class)
@EnableConfigurationProperties(QueueSettings.class)
@EnableScheduling
public class HardenedProfileConfiguration {

    @Bean
    @ConditionalOnMissingBean(ImageGenerator.class)
    ImageGenerator hardenedImageGenerator() {
        return prompt -> "image:" + prompt;
    }

    @Bean(name = "jobExecutionExecutor", destroyMethod = "")
    ThreadPoolExecutor jobExecutionExecutor(final QueueSettings settings) {
        return new ThreadPoolExecutor(
                settings.concurrency(),
                settings.concurrency(),
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(settings.concurrency()),
                Thread.ofPlatform().name("db-job-execution-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        final var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("db-job-control-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }
}
