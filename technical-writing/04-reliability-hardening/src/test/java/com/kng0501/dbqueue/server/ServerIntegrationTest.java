package com.kng0501.dbqueue.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(
        classes = WebServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=${TECHNICAL_WRITING_TEST_DB_URL}",
                "spring.datasource.username=${TECHNICAL_WRITING_TEST_DB_USERNAME}",
                "spring.datasource.password=${TECHNICAL_WRITING_TEST_DB_PASSWORD}",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/04/schema.sql",
                "db-queue.processing-timeout=30s",
                "db-queue.max-attempts=3",
                "db-queue.retry-delay=1s",
                "db-queue.concurrency=2",
                "db-queue.polling-interval=10ms",
                "db-queue.recovery-interval=10ms",
                "db-queue.scheduling-enabled=false",
        }
)
@AutoConfigureMockMvc
public @interface ServerIntegrationTest {
}
