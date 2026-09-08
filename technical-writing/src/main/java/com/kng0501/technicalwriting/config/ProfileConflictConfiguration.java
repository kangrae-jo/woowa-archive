package com.kng0501.technicalwriting.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("baseline & hardened")
public class ProfileConflictConfiguration {

    @Bean
    static BeanFactoryPostProcessor rejectSimultaneousQueueProfiles() {
        return beanFactory -> {
            throw new IllegalStateException("baseline과 hardened 프로필은 동시에 활성화할 수 없습니다.");
        };
    }
}
