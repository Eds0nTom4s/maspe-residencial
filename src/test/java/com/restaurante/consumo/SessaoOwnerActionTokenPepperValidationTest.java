package com.restaurante.consumo;

import com.restaurante.config.SessaoOwnerActionTokenPepperValidator;
import com.restaurante.config.SessaoOwnerActionTokenProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prompt 41.5 — Testes de validação do pepper em produção.
 */
@DisplayName("SessaoOwnerActionTokenPepperValidator — Prompt 41.5")
class SessaoOwnerActionTokenPepperValidationTest {

    @Test
    @DisplayName("prod + pepper blank → falha de startup com código claro")
    void prod_pepper_blank_falha() {
        var props = new SessaoOwnerActionTokenProperties();
        props.setHashPepper("");

        var env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        var validator = new SessaoOwnerActionTokenPepperValidator(props, env);

        assertThatThrownBy(validator::onApplicationReady)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OWNER_ACTION_TOKEN_PEPPER_REQUIRED_IN_PRODUCTION")
                .hasMessageNotContaining("mysecret"); // não imprime valor
    }

    @Test
    @DisplayName("production + pepper blank → falha")
    void production_pepper_blank_falha() {
        var props = new SessaoOwnerActionTokenProperties();
        props.setHashPepper(null);

        var env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"production"});

        var validator = new SessaoOwnerActionTokenPepperValidator(props, env);

        assertThatThrownBy(validator::onApplicationReady)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OWNER_ACTION_TOKEN_PEPPER_REQUIRED_IN_PRODUCTION");
    }

    @Test
    @DisplayName("prod + pepper definido → passa sem exceção")
    void prod_pepper_definido_passa() {
        var props = new SessaoOwnerActionTokenProperties();
        props.setHashPepper("my-very-strong-pepper-value");

        var env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        var validator = new SessaoOwnerActionTokenPepperValidator(props, env);
        validator.onApplicationReady(); // sem exceção
    }

    @Test
    @DisplayName("dev + pepper blank → passa (não exige)")
    void dev_pepper_blank_passa() {
        var props = new SessaoOwnerActionTokenProperties();
        props.setHashPepper("");

        var env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"dev"});

        var validator = new SessaoOwnerActionTokenPepperValidator(props, env);
        validator.onApplicationReady(); // sem exceção
    }

    @Test
    @DisplayName("test + pepper blank → passa")
    void test_pepper_blank_passa() {
        var props = new SessaoOwnerActionTokenProperties();
        props.setHashPepper("");

        var env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"test"});

        var validator = new SessaoOwnerActionTokenPepperValidator(props, env);
        validator.onApplicationReady(); // sem exceção
    }

    @Test
    @DisplayName("sem profiles ativos → passa (default é dev)")
    void sem_profiles_passa() {
        var props = new SessaoOwnerActionTokenProperties();
        props.setHashPepper("");

        var env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{});

        var validator = new SessaoOwnerActionTokenPepperValidator(props, env);
        validator.onApplicationReady(); // sem exceção
    }

    @Test
    @DisplayName("erro não imprime valor do pepper (mensagem não contém o valor)")
    void erro_nao_imprime_pepper() {
        var props = new SessaoOwnerActionTokenProperties();
        props.setHashPepper("");

        var env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        var validator = new SessaoOwnerActionTokenPepperValidator(props, env);

        assertThatThrownBy(validator::onApplicationReady)
                .isInstanceOf(IllegalStateException.class)
                // A mensagem não deve conter nenhum valor de pepper
                .hasMessageNotContaining("=")
                .hasMessageNotContaining("secret");
    }
}
