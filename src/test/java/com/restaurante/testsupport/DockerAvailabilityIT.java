package com.restaurante.testsupport;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate para o profile `-Pit`:
 * - evita falso positivo (BUILD SUCCESS) quando a suíte de ITs é SKIPPED por Docker indisponível
 * - não usa Assumptions para não mascarar o problema como "verde"
 */
@IntegrationTest
class DockerAvailabilityIT {

    @Test
    void dockerMustBeAvailableForIntegrationTests() {
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            available = false;
        }

        assertTrue(
                available,
                "Docker/Testcontainers indisponível. Rode os ITs em um ambiente com Docker acessível " +
                        "ou valide permissões de acesso ao socket (ex.: /var/run/docker.sock)."
        );
    }
}
