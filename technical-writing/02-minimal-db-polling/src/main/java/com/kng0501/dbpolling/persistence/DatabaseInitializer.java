package com.kng0501.dbpolling.persistence;

import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

public final class DatabaseInitializer {

    private final DataSource dataSource;

    public DatabaseInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initialize() {
        var populator = new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"));
        populator.execute(dataSource);
    }
}
