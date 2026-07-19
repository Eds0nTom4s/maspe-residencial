package com.restaurante.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Configuração de CORS para a aplicação.
 * Define um CorsConfigurationSource bean para que o Spring Security
 * aplique os headers CORS ANTES de avaliar autenticação/autorização,
 * incluindo respostas a preflight (OPTIONS).
 *
 * <p>Origins configuráveis via propriedade {@code cors.allowed-origins}
 * (env: {@code CORS_ALLOWED_ORIGINS}), separados por vírgula.
 * Se a propriedade estiver vazia, usa o conjunto de defaults de desenvolvimento.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /** Fallback para ambiente de desenvolvimento/sandbox quando a propriedade não é definida. */
    private static final List<String> DEFAULT_DEV_ORIGINS = Arrays.asList(
        "http://localhost:3000",   // React / CRA
        "http://localhost:3001",   // React alternativo
        "http://localhost:4200",   // Angular
        "http://localhost:5173",   // Vite / Vue 3
        "http://localhost:5174",   // Vite segunda instância
        "http://localhost:8080",   // porta padrão interna
        "http://localhost:8081",   // sandbox-local exposta
        "http://localhost:8082",   // sandbox-local alternativa
        "http://127.0.0.1:5173",
        "http://127.0.0.1:3000",
        "http://127.0.0.1:3001"
        // ⚠️ Adicionar domínios de produção apenas pela env CORS_ALLOWED_ORIGINS
    );

    @Value("${cors.allowed-origins:}")
    private String allowedOriginsProperty;

    /**
     * Resolve a lista efectiva de origins: usa a propriedade se definida,
     * caso contrário aplica os defaults de desenvolvimento.
     */
    List<String> resolveAllowedOrigins() {
        if (StringUtils.hasText(allowedOriginsProperty)) {
            return Arrays.stream(allowedOriginsProperty.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        return DEFAULT_DEV_ORIGINS;
    }

    /**
     * Bean usado pelo Spring Security (.cors(cors -> cors.configurationSource(...))).
     * Garante que headers CORS são injectados mesmo quando a requisição
     * seria bloqueada por falta de autenticação (ex: preflight OPTIONS → 403).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = resolveAllowedOrigins();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "Accept",
            "Origin",
            "X-Requested-With",
            "Idempotency-Key",
            "X-Correlation-Id"
        ));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Configuração MVC (complementar — garante CORS também fora do filtro de segurança) */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = resolveAllowedOrigins();
        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With",
                        "Idempotency-Key", "X-Correlation-Id")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
