package com.restaurante.testsupport;

final class IntegrationTestRuntime {

    private static final String RUNNER_PROPERTY = "consuma.test.runner";

    private IntegrationTestRuntime() {
    }

    static void requireFailsafeRunner() {
        String runner = System.getProperty(RUNNER_PROPERTY, "unknown");
        if ("failsafe".equals(runner)) {
            return;
        }

        throw new IllegalStateException(
                "Teste de integração acionado pelo runner errado (" + runner + "). " +
                        "Use `mvn -Pit -Dit.test=... verify` para rodar ITs com PostgreSQL/Testcontainers."
        );
    }
}
