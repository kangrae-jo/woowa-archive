package com.kng0501.dbpolling.server;

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
                "spring.sql.init.schema-locations=classpath:db/02/schema.sql"
        }
)
@AutoConfigureMockMvc
public @interface ServerIntegrationTest {
}
