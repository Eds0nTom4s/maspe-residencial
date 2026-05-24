package com.restaurante.config;

import com.restaurante.model.enums.OperationalEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Prompt 41.5 — Valida que o hash-pepper do ownerActionToken está definido
 * quando o profile ativo for {@code prod} ou {@code production}.
 * <p>
 * Falha em startup com mensagem clara sem imprimir o valor do pepper.
 * Em profile de desenvolvimento/test, pepper vazio é aceito.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessaoOwnerActionTokenPepperValidator {

    private static final String[] PROD_PROFILES = {"prod", "production", "homolog"};

    private final SessaoOwnerActionTokenProperties tokenProps;
    private final Environment environment;

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationReady() {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProd = Arrays.stream(activeProfiles)
                .anyMatch(p -> Arrays.asList(PROD_PROFILES).contains(p.toLowerCase()));

        if (!isProd) {
            log.debug("[OwnerTokenPepper] Ambiente não-produção — pepper não obrigatório.");
            return;
        }

        String pepper = tokenProps.getHashPepper();
        if (pepper == null || pepper.isBlank()) {
            log.error("[OwnerTokenPepper] OWNER_ACTION_TOKEN_PEPPER_REQUIRED_IN_PRODUCTION — " +
                    "consuma.sessao.owner-action-token.hash-pepper must be configured in production. " +
                    "Defina a variável de ambiente CONSUMA_OWNER_ACTION_TOKEN_PEPPER.");
            // Registra o evento de falha (sem imprimir o valor do pepper)
            log.error("[OwnerTokenPepper] Evento: {}", OperationalEventType.SESSAO_OWNER_ACTION_TOKEN_PEPPER_VALIDATION_FAILED);
            throw new IllegalStateException(
                    "OWNER_ACTION_TOKEN_PEPPER_REQUIRED_IN_PRODUCTION: " +
                    "consuma.sessao.owner-action-token.hash-pepper must be configured in production.");
        }

        log.info("[OwnerTokenPepper] Pepper configurado corretamente para ambiente de produção (profile={}).",
                Arrays.toString(activeProfiles));
    }
}
