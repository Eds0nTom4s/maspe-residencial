package com.restaurante.testsupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@IntegrationTest
public abstract class PostgresTestcontainersConfig {

    private static volatile PostgreSQLContainer<?> POSTGRES;

    private static PostgreSQLContainer<?> postgres() {
        PostgreSQLContainer<?> container = POSTGRES;
        if (container != null) {
            return container;
        }

        synchronized (PostgresTestcontainersConfig.class) {
            container = POSTGRES;
            if (container == null) {
                container = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("consuma_test")
                        .withUsername("consuma")
                        .withPassword("consuma");
                container.start();
                POSTGRES = container;
            }
        }

        return container;
    }

    @BeforeAll
    static void requireDocker() {
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            available = false;
        }
        Assumptions.assumeTrue(available, "Docker indisponível: testes com Testcontainers serão ignorados.");
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres().getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgres().getUsername());
        registry.add("spring.datasource.password", () -> postgres().getPassword());
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Segurança extra: garantir que não usamos H2 em testes
        registry.add("spring.h2.console.enabled", () -> "false");
    }
}
