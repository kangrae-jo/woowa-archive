package com.kng0501.technicalwriting.testsupport;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

public final class SqlCaptureInspector implements StatementInspector {

    private final CopyOnWriteArrayList<String> statements = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(final String sql) {
        statements.add(sql.replaceAll("\\s+", " ").trim());
        return sql;
    }

    public void clear() {
        statements.clear();
    }

    public List<String> statements() {
        return List.copyOf(statements);
    }
}
