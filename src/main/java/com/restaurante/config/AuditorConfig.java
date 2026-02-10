package com.restaurante.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Configuração de auditoria JPA
 * Fornece o usuário atual para campos @CreatedBy e @LastModifiedBy
 * 
 * ETAPA 05: Integrado com Spring Security - captura usuário do contexto JWT
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@Slf4j
public class AuditorConfig {

    /**
     * Provedor de auditor (usuário logado do SecurityContext)
     * 
     * @return AuditorAware que fornece identificador do usuário autenticado
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return new AuditorAwareImpl();
    }

    /**
     * Implementação do AuditorAware
     * Captura o usuário atual do SecurityContext para auditoria
     */
    static class AuditorAwareImpl implements AuditorAware<String> {

        @Override
        public Optional<String> getCurrentAuditor() {
            // Obtém autenticação do SecurityContext (JWT)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated() 
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                String username = authentication.getName();
                log.debug("Auditor capturado: {}", username);
                return Optional.of(username);
            }
            
            // Fallback para "system" quando não há usuário autenticado
            // (ex: inicialização, seeds, operações internas)
            log.debug("Auditor capturado: system (sem autenticação)");
            return Optional.of("system");
        }
    }
}
