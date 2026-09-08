package com.kng0501.technicalwriting.config;

import com.kng0501.dbpolling.application.BaselineSettings;
import com.kng0501.dbpolling.domain.ImageGenerator;
import com.kng0501.dbpolling.persistence.entity.BaselineMonsterEntity;
import com.kng0501.dbpolling.persistence.jpa.BaselineMonsterJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@Profile("baseline")
@ComponentScan(basePackages = {
        "com.kng0501.dbpolling.application",
        "com.kng0501.dbpolling.persistence"
})
@EntityScan(basePackageClasses = BaselineMonsterEntity.class)
@EnableJpaRepositories(basePackageClasses = BaselineMonsterJpaRepository.class)
@EnableConfigurationProperties(BaselineSettings.class)
public class BaselineProfileConfiguration {

    @Bean
    @ConditionalOnMissingBean(ImageGenerator.class)
    ImageGenerator baselineImageGenerator() {
        return prompt -> "image:" + prompt;
    }
}
