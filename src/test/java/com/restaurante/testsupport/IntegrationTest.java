package com.restaurante.testsupport;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca testes de integração que dependem de infra (ex.: Docker/Testcontainers/PostgreSQL).
 *
 * Usado para:
 * - excluir de `mvn test` (Surefire) por padrão;
 * - incluir apenas no profile `-Pit` (Failsafe).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Tag("it")
@ExtendWith(RequireFailsafeRunnerCondition.class)
public @interface IntegrationTest {
}
