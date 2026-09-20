package com.kng0501.measurement;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
@EnableConfigurationProperties(MeasurementProperties.class)
public class MeasurementApplication {

    public static void main(final String[] args) {
        final ConfigurableApplicationContext context = new SpringApplicationBuilder(MeasurementApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
        System.exit(SpringApplication.exit(context));
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    HttpClient measurementHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
}
