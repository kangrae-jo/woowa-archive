package com.kng0501.dbqueue.worker;

import com.kng0501.dbqueue.application.ExpiredJobRecovery;
import com.kng0501.dbqueue.application.ExpiredJobTransition;
import com.kng0501.dbqueue.application.JobPollingTasks;
import com.kng0501.dbqueue.application.JobQueue;
import com.kng0501.dbqueue.application.JobScheduler;
import com.kng0501.dbqueue.application.JobWorker;
import com.kng0501.dbqueue.domain.ImageGenerator;
import com.kng0501.dbqueue.domain.QueueSettings;
import com.kng0501.dbqueue.persistence.HardenedPersistenceConfiguration;
import com.kng0501.dbqueue.persistence.jpa.ImageGenerationJobJpaRepository;
import com.kng0501.technicalwriting.config.MockImageGeneratorSettings;
import com.kng0501.technicalwriting.config.MockImageGeneratorSupport;
import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication(scanBasePackageClasses = HardenedImageWorkerApplication.class)
@Import(HardenedPersistenceConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties(MockImageGeneratorSettings.class)
public class HardenedImageWorkerApplication {

    public static void main(final String[] args) {
        new SpringApplicationBuilder(HardenedImageWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @Bean
    @ConditionalOnMissingBean(ImageGenerator.class)
    ImageGenerator hardenedImageGenerator(final MockImageGeneratorSettings settings) {
        return prompt -> MockImageGeneratorSupport.generate(prompt, settings.delay());
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

    @Bean
    ExpiredJobTransition expiredJobTransition(
            final ImageGenerationJobJpaRepository jobs,
            final QueueSettings settings
    ) {
        return new ExpiredJobTransition(jobs, settings);
    }

    @Bean
    ExpiredJobRecovery expiredJobRecovery(
            final Clock clock,
            final ImageGenerationJobJpaRepository jobs,
            final ExpiredJobTransition transition
    ) {
        return new ExpiredJobRecovery(clock, jobs, transition);
    }

    @Bean
    JobWorker jobWorker(final JobQueue queue, final ImageGenerator generator) {
        return new JobWorker(queue, generator);
    }

    @Bean
    JobScheduler jobScheduler(
            final JobQueue queue,
            final JobWorker worker,
            final QueueSettings settings,
            final ThreadPoolExecutor jobExecutionExecutor
    ) {
        return new JobScheduler(queue, worker, settings, jobExecutionExecutor);
    }

    @Bean
    @ConditionalOnProperty(prefix = "db-queue", name = "scheduling-enabled", havingValue = "true")
    JobPollingTasks jobPollingTasks(
            final ExpiredJobRecovery recovery,
            final JobScheduler scheduler
    ) {
        return new JobPollingTasks(recovery, scheduler);
    }
}
