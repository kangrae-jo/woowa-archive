package com.kng0501.dbpolling.persistence;

import com.kng0501.dbpolling.persistence.entity.BaselineMonsterEntity;
import com.kng0501.dbpolling.persistence.jpa.BaselineMonsterJpaRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        JpaMonsterRepository.class,
        JpaImageGenerationRequestRepository.class
})
@EntityScan(basePackageClasses = BaselineMonsterEntity.class)
@EnableJpaRepositories(basePackageClasses = BaselineMonsterJpaRepository.class)
public class BaselinePersistenceConfiguration {

    @Bean
    Clock baselineClock() {
        return Clock.systemUTC();
    }
}
