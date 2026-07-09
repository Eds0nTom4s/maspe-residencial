package com.restaurante.testsupport;

import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantCardapioConfig;
import com.restaurante.repository.TenantCardapioConfigRepository;
import com.restaurante.repository.TenantRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;

@ExtendWith(RequireFailsafeRunnerCondition.class)
@Testcontainers
@IntegrationTest
public abstract class PostgresTestcontainersConfig {

    private static volatile PostgreSQLContainer<?> POSTGRES;

    @Autowired(required = false)
    private TenantRepository cardapioTestTenantRepository;

    @Autowired(required = false)
    private TenantCardapioConfigRepository cardapioTestConfigRepository;

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
                        .withPassword("consuma")
                        .withCommand("postgres", "-c", "max_connections=200")
                        .withStartupAttempts(3)
                        .withStartupTimeout(Duration.ofMinutes(2))
                        .withReuse(true);
                container.start();
                POSTGRES = container;
            }
        }

        return container;
    }

    @BeforeAll
    static void requireDocker() {
        IntegrationTestRuntime.requireFailsafeRunner();
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            available = false;
        }
        Assertions.assertTrue(
                available,
                "Docker/Testcontainers indisponível. Rode os ITs com `mvn -Pit -Dit.test=... verify` em um ambiente com Docker acessível."
        );
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        IntegrationTestRuntime.requireFailsafeRunner();
        registry.add("spring.datasource.url", () -> postgres().getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgres().getUsername());
        registry.add("spring.datasource.password", () -> postgres().getPassword());
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Segurança extra: garantir que não usamos H2 em testes
        registry.add("spring.h2.console.enabled", () -> "false");
    }

    protected void publicarCardapioForTest(Long tenantId) {
        if (cardapioTestTenantRepository == null || cardapioTestConfigRepository == null) {
            throw new IllegalStateException("Repositórios de cardápio não disponíveis no contexto de teste.");
        }
        Tenant tenant = cardapioTestTenantRepository.findById(tenantId).orElseThrow();
        TenantCardapioConfig config = cardapioTestConfigRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    TenantCardapioConfig created = new TenantCardapioConfig();
                    created.setTenant(tenant);
                    return created;
                });
        LocalDateTime now = LocalDateTime.now();
        config.setCardapioPublicado(true);
        config.setCardapioPublicadoEm(now);
        config.setCardapioAtualizadoEm(now);
        config.setCardapioMotivoDespublicacao(null);
        cardapioTestConfigRepository.saveAndFlush(config);
    }
}
