package dev.novadev.novabank.storage.sql;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * {@link DataSource} decoree qui injecte une panne sur les instructions choisies
 * (et sur {@code commit}, vu comme l'instruction {@code "COMMIT"}).
 */
final class FaultInjectingDataSource implements DataSource, AutoCloseable {

    /**
     * Panne : remplace l'execution de l'instruction visee.
     */
    @FunctionalInterface
    interface Fault {
        Object apply(String sql) throws SQLException;
    }

    private final DataSource delegate;
    private final AtomicInteger remaining = new AtomicInteger();
    private final AtomicInteger hits = new AtomicInteger();
    private volatile Predicate<String> target = sql -> false;
    private volatile Fault fault;

    FaultInjectingDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    /**
     * Les {@code times} prochaines executions d'une instruction reconnue par {@code matcher}
     * subissent {@code injected}.
     */
    void inject(Predicate<String> matcher, int times, Fault injected) {
        this.fault = injected;
        this.target = matcher;
        hits.set(0);
        remaining.set(times);
    }

    void clear() {
        remaining.set(0);
    }

    /** Nombre d'executions de l'instruction visee depuis {@link #inject}, pannes comprises. */
    int hits() {
        return hits.get();
    }

    static Fault failing(String message) {
        return sql -> {
            throw new SQLException("Panne injectee : " + message + " [" + sql + "]");
        };
    }

    static Fault rethrowing(SQLException failure) {
        return sql -> {
            throw failure;
        };
    }

    static Fault noRowAffected() {
        return sql -> 0;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = delegate.getConnection();
        return proxy(Connection.class, connection, (method, args) -> {
            if (method.getName().equals("commit")) {
                return intercept("COMMIT", () -> method.invoke(connection, args));
            }
            Object result = method.invoke(connection, args);
            if (result instanceof PreparedStatement prepared && args != null && args[0] instanceof String sql) {
                return statementProxy(PreparedStatement.class, prepared, sql);
            }
            if (result instanceof Statement statement && !(result instanceof PreparedStatement)) {
                return statementProxy(Statement.class, statement, null);
            }
            return result;
        });
    }

    private <S extends Statement> S statementProxy(Class<S> type, S statement, String preparedSql) {
        return proxy(type, statement, (method, args) -> {
            if (method.getName().startsWith("execute")) {
                String sql = preparedSql != null ? preparedSql : (args != null && args.length > 0 ? (String) args[0] : "");
                return intercept(sql, () -> method.invoke(statement, args));
            }
            return method.invoke(statement, args);
        });
    }

    private Object intercept(String sql, Invocation proceed) throws Throwable {
        if (target.test(sql)) {
            hits.incrementAndGet();
            if (remaining.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
                return fault.apply(sql);
            }
        }
        return proceed.invoke();
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke() throws Throwable;
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(Method method, Object[] args) throws Throwable;
    }

    private static <T> T proxy(Class<T> type, Object target, Handler handler) {
        InvocationHandler invocationHandler = (proxy, method, args) -> {
            try {
                return handler.handle(method, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        };
        return type.cast(Proxy.newProxyInstance(FaultInjectingDataSource.class.getClassLoader(), new Class<?>[] {type},
                invocationHandler));
    }

    @Override
    public void close() {
        if (delegate instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException("Non supporte");
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger("novabank-test");
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        return delegate.unwrap(type);
    }

    @Override
    public boolean isWrapperFor(Class<?> type) throws SQLException {
        return delegate.isWrapperFor(type);
    }
}
