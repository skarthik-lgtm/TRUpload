package com.trupload.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SqlServerConfig {

    @Bean
    JdbcTemplate sqlServerJdbcTemplate(
            @Value("${app.sqlserver.url}") String url,
            @Value("${app.sqlserver.username}") String username,
            @Value("${app.sqlserver.password}") String password) {
        DataSource dataSource = DataSourceBuilder.create()
                .driverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
                .url(url)
                .username(username)
                .password(password)
                .build();
        return new JdbcTemplate(dataSource);
    }
}
