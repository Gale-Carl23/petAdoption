package com.petdonation.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Adapts Vercel Marketplace Postgres URLs (postgresql://...) to JDBC. */
@Configuration
@Profile("!local")
public class ProductionDataSourceConfiguration {

    @Bean
    HikariDataSource dataSource(
            @Value("${DB_URL:${DATABASE_URL:}}") String configuredUrl,
            @Value("${DB_USERNAME:}") String configuredUsername,
            @Value("${DB_PASSWORD:}") String configuredPassword,
            @Value("${DB_POOL_MAX_SIZE:10}") int maximumPoolSize,
            @Value("${DB_POOL_MIN_IDLE:2}") int minimumIdle) {
        if (configuredUrl == null || configuredUrl.isBlank()) {
            throw new IllegalStateException(
                    "Database is not configured. Connect a Postgres database in Vercel and set DATABASE_URL.");
        }

        DatabaseConnection connection = parse(configuredUrl.trim());
        String username = configuredUsername.isBlank() ? connection.username() : configuredUsername;
        String password = configuredPassword.isBlank() ? connection.password() : configuredPassword;
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Database credentials are missing. DATABASE_URL must include credentials, or set DB_USERNAME and DB_PASSWORD.");
        }

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(connection.jdbcUrl());
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setMaximumPoolSize(Math.max(1, maximumPoolSize));
        dataSource.setMinimumIdle(Math.max(0, Math.min(minimumIdle, maximumPoolSize)));
        dataSource.setConnectionTimeout(30_000);
        dataSource.setValidationTimeout(5_000);
        dataSource.setMaxLifetime(1_500_000);
        return dataSource;
    }

    private static DatabaseConnection parse(String value) {
        if (value.startsWith("jdbc:postgresql://")) {
            return new DatabaseConnection(value, null, null);
        }
        String normalized = value.startsWith("postgres://")
                ? "postgresql://" + value.substring("postgres://".length())
                : value;
        try {
            URI uri = URI.create(normalized);
            if (!"postgresql".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalStateException("DATABASE_URL must be a PostgreSQL connection URL.");
            }
            String userInfo = uri.getRawUserInfo();
            String username = null;
            String password = null;
            if (userInfo != null) {
                int separator = userInfo.indexOf(':');
                username = decode(separator < 0 ? userInfo : userInfo.substring(0, separator));
                password = separator < 0 ? null : decode(userInfo.substring(separator + 1));
            }
            String jdbcUrl = "jdbc:postgresql://" + uri.getRawAuthority().substring(
                    userInfo == null ? 0 : userInfo.length() + 1)
                    + (uri.getRawPath() == null ? "" : uri.getRawPath())
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                    + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
            return new DatabaseConnection(jdbcUrl, username, password);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("DATABASE_URL is not a valid PostgreSQL URL.", ex);
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private record DatabaseConnection(String jdbcUrl, String username, String password) {}
}
