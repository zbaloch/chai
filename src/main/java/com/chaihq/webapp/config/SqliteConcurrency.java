package com.chaihq.webapp.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * SQLite allows one writer at a time. By default a transaction only asks for the write lock when
 * it first writes, so two requests that both read and then write (two people chatting, say) can
 * deadlock, and one fails with "database is locked". Starting every transaction with the write
 * lock (IMMEDIATE) makes them queue instead, waiting up to the busy timeout for their turn.
 * Only applies to SQLite; other databases are left alone.
 */
@Component
public class SqliteConcurrency implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof HikariDataSource dataSource
                && dataSource.getJdbcUrl() != null && dataSource.getJdbcUrl().startsWith("jdbc:sqlite:")) {
            dataSource.addDataSourceProperty("transaction_mode", "IMMEDIATE");
            dataSource.addDataSourceProperty("busy_timeout", "10000");
        }
        return bean;
    }
}
