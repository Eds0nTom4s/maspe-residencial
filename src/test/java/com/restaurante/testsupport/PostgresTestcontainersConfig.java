package com.restaurante.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Duration;

public abstract class PostgresTestcontainersConfig {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("consuma_test")
            .withUsername("consuma")
            .withPassword("consuma")
            .withStartupAttempts(3)
            .withStartupTimeout(Duration.ofMinutes(2))
            .withReuse(true);

    private static volatile boolean STARTED = false;

    private static void startOnceOrThrow() {
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            available = false;
        }
        if (!available) {
            throw new IllegalStateException("Docker indisponível: Testcontainers não consegue iniciar PostgreSQL.");
        }

        if (!STARTED) {
            synchronized (PostgresTestcontainersConfig.class) {
                if (!STARTED) {
                    POSTGRES.start();
                    Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
                    STARTED = true;
                }
            }
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        startOnceOrThrow();

        String jdbcUrl = POSTGRES.getJdbcUrl();
        String username = POSTGRES.getUsername();
        String password = POSTGRES.getPassword();

        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Segurança extra: garantir que não usamos H2 em testes
        registry.add("spring.h2.console.enabled", () -> "false");
    }
}
