package com.kng0501.dbpolling.server;

import com.kng0501.dbpolling.application.ImageGenerationService;
import com.kng0501.dbpolling.persistence.BaselinePersistenceConfiguration;
import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackageClasses = BaselineWebServerApplication.class)
@Import(BaselinePersistenceConfiguration.class)
public class BaselineWebServerApplication {

    public static void main(final String[] args) {
        new SpringApplicationBuilder(BaselineWebServerApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(args);
    }

    @Bean
    ImageGenerationService imageGenerationService(
            final MonsterRepository monsters,
            final ImageGenerationRequestRepository requests
    ) {
        return new ImageGenerationService(monsters, requests);
    }
}
