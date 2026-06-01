package com.restaurante.testsupport;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;

@Order(0)
public class RequireFailsafeRunnerCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult ENABLED =
            ConditionEvaluationResult.enabled("Integration test runner validated.");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String runner = System.getProperty("consuma.test.runner", "unknown");
        if ("failsafe".equals(runner)) {
            return ENABLED;
        }

        throw new ExtensionConfigurationException(
                "Teste de integração acionado pelo runner errado (" + runner + "). " +
                        "Use `mvn -Pit -Dit.test=... verify` para rodar ITs com PostgreSQL/Testcontainers."
        );
    }
}
