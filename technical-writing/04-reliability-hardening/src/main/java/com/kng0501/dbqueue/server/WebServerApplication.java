package com.kng0501.dbqueue.server;

import java.time.Clock;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class WebServerApplication {

    public static void main(final String[] args) {
        new SpringApplicationBuilder(WebServerApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
