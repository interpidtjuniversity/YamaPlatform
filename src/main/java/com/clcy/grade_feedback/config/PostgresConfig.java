package com.clcy.grade_feedback.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PostgreSQL 数据源配置(独立于 MySQL 主库).
 * 仅服务于 audio_transcripts 表, 通过 JdbcTemplate 访问, 不接入 MyBatis.
 */
@Configuration
public class PostgresConfig {

    @Value("${postgres.datasource.url}")
    private String url;

    @Value("${postgres.datasource.username}")
    private String username;

    @Value("${postgres.datasource.password}")
    private String password;

    @Value("${postgres.datasource.driverClassName:org.postgresql.Driver}")
    private String driverClassName;

    @Bean(name = "pgDataSource")
    public DataSource pgDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setPoolName("PostgresHikariPool");
        return new HikariDataSource(config);
    }

    @Bean(name = "pgJdbcTemplate")
    public JdbcTemplate pgJdbcTemplate(@Qualifier("pgDataSource") DataSource pgDataSource) {
        return new JdbcTemplate(pgDataSource);
    }
}
