package com.kng0501.dbqueue.persistence;

import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

public final class QueueDatabaseInitializer {
    private final DataSource dataSource;

    public QueueDatabaseInitializer(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initialize() {
        new ResourceDatabasePopulator(new ClassPathResource("db/hardening/schema.sql"))
                .execute(dataSource);
    }
}
