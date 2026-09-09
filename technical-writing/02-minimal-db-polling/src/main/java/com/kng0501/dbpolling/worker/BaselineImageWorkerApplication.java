package com.kng0501.dbpolling.worker;

import com.kng0501.dbpolling.application.BaselineSettings;
import com.kng0501.dbpolling.application.DbPollingScheduler;
import com.kng0501.dbpolling.application.DbPollingWorker;
import com.kng0501.dbpolling.domain.ImageGenerator;
import com.kng0501.dbpolling.persistence.BaselinePersistenceConfiguration;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;
import com.kng0501.technicalwriting.config.MockImageGeneratorSettings;
import com.kng0501.technicalwriting.config.MockImageGeneratorSupport;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackageClasses = BaselineImageWorkerApplication.class)
@Import(BaselinePersistenceConfiguration.class)
@EnableConfigurationProperties({BaselineSettings.class, MockImageGeneratorSettings.class})
public class BaselineImageWorkerApplication {

    public static void main(final String[] args) {
        new SpringApplicationBuilder(BaselineImageWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @Bean
    @ConditionalOnMissingBean(ImageGenerator.class)
    ImageGenerator baselineImageGenerator(final MockImageGeneratorSettings settings) {
        return prompt -> {
            try {
                return MockImageGeneratorSupport.generate(prompt, settings.delay());
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("모의 이미지 생성을 중단했습니다.", interrupted);
            }
        };
    }

    @Bean
    DbPollingWorker dbPollingWorker(
            final ImageGenerationRequestRepository requests,
            final MonsterRepository monsters,
            final ImageGenerator generator
    ) {
        return new DbPollingWorker(requests, monsters, generator);
    }

    @Bean
    DbPollingScheduler dbPollingScheduler(
            final DbPollingWorker worker,
            final BaselineSettings settings
    ) {
        return new DbPollingScheduler(worker, settings);
    }
}
