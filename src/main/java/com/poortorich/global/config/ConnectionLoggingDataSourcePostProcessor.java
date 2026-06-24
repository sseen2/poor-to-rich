package com.poortorich.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@Profile({"connection-log", "loadtest"})
public class ConnectionLoggingDataSourcePostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if ("dataSource".equals(beanName) && bean instanceof DataSource dataSource) {
            return new ConnectionLoggingDataSource(dataSource);
        }

        return bean;
    }

    private static class ConnectionLoggingDataSource extends DelegatingDataSource {

        private final AtomicLong sequence = new AtomicLong();

        private ConnectionLoggingDataSource(DataSource targetDataSource) {
            super(targetDataSource);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return wrap(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return wrap(super.getConnection(username, password));
        }

        private Connection wrap(Connection connection) {
            long id = sequence.incrementAndGet();
            long borrowedAt = System.currentTimeMillis();
            String borrowedThread = Thread.currentThread().getName();

            log.info("[DB_CONNECTION_BORROW] id={}, thread={}", id, borrowedThread);

            return (Connection) Proxy.newProxyInstance(
                    connection.getClass().getClassLoader(),
                    new Class[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("close".equals(method.getName())) {
                            long usedMillis = System.currentTimeMillis() - borrowedAt;
                            log.info(
                                    "[DB_CONNECTION_RETURN] id={}, borrowedThread={}, returnThread={}, usedMillis={}",
                                    id,
                                    borrowedThread,
                                    Thread.currentThread().getName(),
                                    usedMillis
                            );
                        }

                        try {
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException e) {
                            throw e.getTargetException();
                        }
                    }
            );
        }
    }
}
