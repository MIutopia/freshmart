package com.freshmart.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class DomainDataSourceConfig {
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties coreDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("dataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource coreDataSource(@Qualifier("coreDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @ConfigurationProperties("storage.datasource.user")
    DataSourceProperties userDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("userDataSource")
    @ConfigurationProperties("storage.datasource.user.hikari")
    HikariDataSource userDataSource(@Qualifier("userDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean("userJdbcTemplate")
    JdbcTemplate userJdbcTemplate(@Qualifier("userDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean("userTransactionManager")
    PlatformTransactionManager userTransactionManager(@Qualifier("userDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    @ConfigurationProperties("storage.datasource.merchant")
    DataSourceProperties merchantDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("merchantDataSource")
    @ConfigurationProperties("storage.datasource.merchant.hikari")
    HikariDataSource merchantDataSource(@Qualifier("merchantDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean("merchantJdbcTemplate")
    JdbcTemplate merchantJdbcTemplate(@Qualifier("merchantDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean("merchantTransactionManager")
    PlatformTransactionManager merchantTransactionManager(@Qualifier("merchantDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    @ConfigurationProperties("storage.datasource.log")
    DataSourceProperties logDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("logDataSource")
    @ConfigurationProperties("storage.datasource.log.hikari")
    HikariDataSource logDataSource(@Qualifier("logDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean("logJdbcTemplate")
    JdbcTemplate logJdbcTemplate(@Qualifier("logDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
