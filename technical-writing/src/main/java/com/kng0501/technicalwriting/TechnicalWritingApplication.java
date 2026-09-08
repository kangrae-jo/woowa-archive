package com.kng0501.technicalwriting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(proxyBeanMethods = false)
public class TechnicalWritingApplication {

    public static void main(final String[] args) {
        SpringApplication.run(TechnicalWritingApplication.class, args);
    }
}
