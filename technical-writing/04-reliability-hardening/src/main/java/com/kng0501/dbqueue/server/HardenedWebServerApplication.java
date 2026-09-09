package com.kng0501.dbqueue.server;

import com.kng0501.dbqueue.persistence.HardenedPersistenceConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackageClasses = HardenedWebServerApplication.class)
@Import(HardenedPersistenceConfiguration.class)
public class HardenedWebServerApplication {

    public static void main(final String[] args) {
        new SpringApplicationBuilder(HardenedWebServerApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(args);
    }
}
