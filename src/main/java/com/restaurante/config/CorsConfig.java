package com.restaurante.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final List<String> ALLOWED_ORIGINS = Arrays.asList(
        "http://localhost:3000",   // React / CRA
        "http://localhost:3001",   // React alternativo
        "http://localhost:4200",   // Angular
        "http://localhost:5173",   // Vite / Vue 3
        "http://localhost:5174",   // Vite segunda instância
        "http://localhost:8081",   // Vue CLI
        "http://127.0.0.1:5173",
        "http://127.0.0.1:3000",
        "http://127.0.0.1:3001"
        // ⚠️ Adicionar domínios de produção aqui antes do deploy
    );

    /**
     * Bean usado pelo Spring Security (.cors(cors -> cors.configurationSource(...))).
     * Garante que headers CORS são injectados mesmo quando a requisição
     * seria bloqueada por falta de autenticação (ex: preflight OPTIONS → 403).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(ALLOWED_ORIGINS);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "Accept",
            "Origin",
            "X-Requested-With"
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
        registry.addMapping("/api/**")
                .allowedOrigins(ALLOWED_ORIGINS.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
