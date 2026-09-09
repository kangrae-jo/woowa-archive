package com.kng0501.dbpolling.worker;

import com.kng0501.dbpolling.worker.application.DbPollingScheduler;
import com.kng0501.dbpolling.worker.application.DbPollingWorker;
import com.kng0501.dbpolling.worker.application.MockImageGenerator;
import com.kng0501.dbpolling.worker.application.MockImageGeneratorSettings;
import com.kng0501.dbpolling.worker.application.WorkerSettings;
import com.kng0501.dbpolling.worker.domain.ImageGenerator;
import com.kng0501.dbpolling.worker.persistence.ImageResultUpdater;
import com.kng0501.dbpolling.worker.persistence.jpa.ImageGenerationRequestJpaRepository;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties({WorkerSettings.class, MockImageGeneratorSettings.class})
public class ImageWorkerApplication {

    public static void main(final String[] args) {
        new SpringApplicationBuilder(ImageWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @Bean
    @ConditionalOnMissingBean(ImageGenerator.class)
    ImageGenerator imageGenerator(final MockImageGeneratorSettings settings) {
        return new MockImageGenerator(settings);
    }

    @Bean
    DbPollingWorker dbPollingWorker(
            final ImageGenerationRequestJpaRepository requests,
            final ImageResultUpdater results,
            final ImageGenerator generator
    ) {
        return new DbPollingWorker(requests, results, generator);
    }

    @Bean
    DbPollingScheduler dbPollingScheduler(
            final DbPollingWorker worker,
            final WorkerSettings settings
    ) {
        return new DbPollingScheduler(worker, settings);
    }
}
