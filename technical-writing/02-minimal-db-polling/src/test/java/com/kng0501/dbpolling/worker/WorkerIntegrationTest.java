package com.kng0501.dbpolling.worker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(
        classes = ImageWorkerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=${TECHNICAL_WRITING_TEST_DB_URL}",
                "spring.datasource.username=${TECHNICAL_WRITING_TEST_DB_USERNAME}",
                "spring.datasource.password=${TECHNICAL_WRITING_TEST_DB_PASSWORD}",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/02/schema.sql",
                "db-polling-worker.polling-interval=10ms",
                "db-polling-worker.scheduling-enabled=false",
                "image-generator.delay=0s"
        }
)
@Import(WorkerTestConfiguration.class)
public @interface WorkerIntegrationTest {
}
