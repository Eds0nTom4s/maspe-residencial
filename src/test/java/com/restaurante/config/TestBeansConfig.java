package com.restaurante.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Beans mínimos para permitir boot do contexto Spring em testes unitários/integrados.
 *
 * Motivo:
 * - SecurityConfig está desabilitado em profile 'test' (@Profile("!test"))
 * - vários beans (ex.: AuthService) dependem de AuthenticationManager
 *
 * Este stub NÃO implementa autenticação real; é apenas para manter o bootstrap estável
 * até que exista uma estratégia de testes mais completa (ex.: @Import(TestSecurityConfig) em E2E).
 */
@Configuration
@Profile("test")
public class TestBeansConfig {

    @Bean
    @Primary
    AuthenticationManager authenticationManager() {
        return authentication -> authentication;
    }

    @Bean
    @Primary
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
