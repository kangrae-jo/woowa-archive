package com.kng0501.dbpolling.support;

import com.kng0501.dbpolling.persistence.DatabaseInitializer;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class TestDatabase {

    private TestDatabase() {
    }

    public static DataSource createInitializedDataSource() {
        var dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        new DatabaseInitializer(dataSource).initialize();
        return dataSource;
    }
}
