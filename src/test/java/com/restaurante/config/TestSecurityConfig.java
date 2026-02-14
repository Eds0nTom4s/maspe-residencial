package com.restaurante.config;

import com.restaurante.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração de Security para Testes E2E
 * 
 * OBJETIVO: Permitir acesso sem autenticação em testes E2E
 * 
 * IMPORTANTE: Esta configuração NUNCA deve ser usada em produção!
 * É ativada com profile 'test' e substitui completamente o SecurityConfig.
 * 
 * SEPARAÇÃO DE RESPONSABILIDADES:
 * - SecurityConfig → Produção (com JWT, roles, etc.)
 * - TestSecurityConfig → Testes E2E (sem autenticação)
 */
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = false) // Desabilita @PreAuthorize para testes
@RequiredArgsConstructor
public class TestSecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    @Primary
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        
        return http.build();
    }
    
    /**
     * AuthenticationManager para testes
     * Necessário para AuthService funcionar
     */
    @Bean
    @Primary
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    
    /**
     * PasswordEncoder para testes
     * Necessário para criar usuários no @BeforeEach
     */
    @Bean
    @Primary
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    /**
     * AuthenticationProvider para testes
     * Necessário para AuthenticationManager funcionar
     */
    @Bean
    @Primary
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }
}
