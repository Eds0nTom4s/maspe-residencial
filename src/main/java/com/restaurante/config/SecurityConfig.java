package com.restaurante.config;

import com.restaurante.security.CustomUserDetailsService;
import com.restaurante.security.JwtAuthenticationFilter;
import com.restaurante.security.JwtSecurityExceptionHandlers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Configuração de segurança do Spring Security
 * 
 * IMPORTANTE: Esta configuração é DESABILITADA em ambiente de teste (profile 'test')
 * para permitir testes E2E sem autenticação. Em testes, usar TestSecurityConfig.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Profile("!test")
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtSecurityExceptionHandlers.JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtSecurityExceptionHandlers.JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // Endpoints públicos de autenticação e cardápio
                        .requestMatchers("/api/auth/**", "/auth/**").permitAll()
                        .requestMatchers("/api/public/**", "/public/**").permitAll()

                        // Debug endpoints: restritos por @Profile("!prod") + @PreAuthorize("ADMIN") no controller
                        // Sem regra de permitAll aqui — autenticação obrigatória

                        // Documentação (apenas em não-prod; considere bloquear em produção)
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()

                        // Actuator: apenas /health é público (load balancers); o resto requer ADMIN
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        // H2 Console (apenas perfil dev)
                        .requestMatchers("/h2-console/**", "/api/h2-console/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        
                        // CORS preflight (OPTIONS)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        
                        // Webhooks e callbacks (AppyPay)
                        .requestMatchers("/api/pagamentos/callback").permitAll()
                        .requestMatchers("/pagamentos/webhook").permitAll()
                        
                        // WebSocket endpoints (SockJS handshake deve ser permitido)
                        .requestMatchers("/ws/**", "/api/ws/**").permitAll()
                        
                        // Qualquer outra requisição requer autenticação
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // Permitir H2 Console (desenvolvimento) - desabilita frame protection e relaxa CSP
        http.headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-ancestors 'self'; form-action 'self';"))
                .contentTypeOptions(contentType -> contentType.disable()));

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        log.info("🔧 Configurando DaoAuthenticationProvider...");
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        log.info("✅ DaoAuthenticationProvider configurado com BCryptPasswordEncoder");
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        log.info("🔧 Configurando AuthenticationManager...");
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        log.info("🔧 Criando BCryptPasswordEncoder...");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        log.info("✅ BCryptPasswordEncoder criado - Strength: 10 (default)");
        return encoder;
    }
}
