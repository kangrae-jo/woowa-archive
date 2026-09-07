package com.kng0501.dbqueue.support;

import com.kng0501.dbqueue.persistence.QueueDatabaseInitializer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class QueueTestDatabase implements AutoCloseable {
    public final ControlledDataSource dataSource;
    public final JdbcTemplate jdbc;

    public QueueTestDatabase() {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:queue-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""
        );
        dataSource = new ControlledDataSource(source);
        jdbc = new JdbcTemplate(dataSource);
        new QueueDatabaseInitializer(dataSource).initialize();
    }

    public int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @Override
    public void close() {
        jdbc.execute("SHUTDOWN");
    }

    public static final class ControlledDataSource extends AbstractDataSource {
        private final DataSource delegate;
        private final AtomicBoolean failNextCandidate = new AtomicBoolean();
        public final CountDownLatch candidateFailureObserved = new CountDownLatch(1);

        private ControlledDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        public void failNextCandidateQuery() {
            failNextCandidate.set(true);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return intercept(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return intercept(delegate.getConnection(username, password));
        }

        private Connection intercept(Connection connection) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                && sql.contains("SELECT job_id FROM image_generation_job")
                                && failNextCandidate.compareAndSet(true, false)) {
                            candidateFailureObserved.countDown();
                            throw new SQLTransientConnectionException("injected candidate query failure");
                        }
                        try {
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }
    }
}
