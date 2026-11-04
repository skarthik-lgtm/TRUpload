
package com.trupload.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class SqlServerConfig {

    @Bean(name = "sqlServerDataSource")
    DataSource sqlServerDataSource(
            @Value("${app.sqlserver.url}") String url,
            @Value("${app.sqlserver.username}") String username,
            @Value("${app.sqlserver.password}") String password) {

        return DataSourceBuilder.create()
                .driverClassName(
                        "com.microsoft.sqlserver.jdbc.SQLServerDriver")
                .url(url)
                .username(username)
                .password(password)
                .build();
    }

    @Bean(name = "sqlServerJdbcTemplate")
    JdbcTemplate sqlServerJdbcTemplate(
            @Qualifier("sqlServerDataSource") DataSource dataSource) {

        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "sqlServerTransactionManager")
    PlatformTransactionManager sqlServerTransactionManager(
            @Qualifier("sqlServerDataSource") DataSource dataSource) {

        return new DataSourceTransactionManager(dataSource);
    }
}
