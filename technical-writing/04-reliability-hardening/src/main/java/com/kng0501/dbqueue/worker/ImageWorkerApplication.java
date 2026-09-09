package com.kng0501.dbqueue.worker;

import com.kng0501.dbqueue.worker.application.ExpiredJobRecovery;
import com.kng0501.dbqueue.worker.application.ExpiredJobTransition;
import com.kng0501.dbqueue.worker.application.JobPollingTasks;
import com.kng0501.dbqueue.worker.application.JobQueue;
import com.kng0501.dbqueue.worker.application.JobScheduler;
import com.kng0501.dbqueue.worker.application.JobWorker;
import com.kng0501.dbqueue.worker.application.MockImageGenerator;
import com.kng0501.dbqueue.worker.application.MockImageGeneratorSettings;
import com.kng0501.dbqueue.worker.domain.ImageGenerator;
import com.kng0501.dbqueue.worker.domain.QueueSettings;
import com.kng0501.dbqueue.worker.persistence.jpa.ImageGenerationJobJpaRepository;
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
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({QueueSettings.class, MockImageGeneratorSettings.class})
public class ImageWorkerApplication {

    public static void main(final String[] args) {
        new SpringApplicationBuilder(ImageWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(ImageGenerator.class)
    ImageGenerator imageGenerator(final MockImageGeneratorSettings settings) {
        return new MockImageGenerator(settings);
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
