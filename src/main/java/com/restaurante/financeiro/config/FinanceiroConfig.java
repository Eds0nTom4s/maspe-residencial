package com.restaurante.financeiro.config;

import com.restaurante.financeiro.gateway.appypay.AppyPayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuração do módulo financeiro
 * 
 * RESPONSABILIDADES:
 * - Configurar RestTemplate para gateway
 * - Validar properties na inicialização
 * - Logs de configuração
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class FinanceiroConfig {
    
    private final AppyPayProperties appyPayProperties;
    
    /**
     * RestTemplate dedicado para chamadas ao gateway
     * 
     * Configurações:
     * - Timeout configurável
     * - Interceptor para logs (se debug ativo)
     * - Error handling customizado
     */
    @Bean(name = "appyPayRestTemplate")
    public RestTemplate appyPayRestTemplate(RestTemplateBuilder builder) {
        log.info("Configurando RestTemplate para AppyPay");
        
        // Valida properties
        try {
            appyPayProperties.validate();
            log.info("AppyPay configurado: baseUrl={}, mock={}", 
                appyPayProperties.getBaseUrl(), 
                appyPayProperties.isMock()
            );
        } catch (IllegalStateException e) {
            log.warn("AppyPay properties incompletas: {}. Modo MOCK será usado.", e.getMessage());
        }
        
        RestTemplateBuilder restTemplateBuilder = builder
            .setConnectTimeout(Duration.ofMillis(appyPayProperties.getTimeoutMs()))
            .setReadTimeout(Duration.ofMillis(appyPayProperties.getTimeoutMs()));
        
        // Adiciona interceptor de log se debug ativo
        if (appyPayProperties.isDebug()) {
            restTemplateBuilder = restTemplateBuilder
                .interceptors(loggingInterceptor());
        }
        
        return restTemplateBuilder.build();
    }
    
    /**
     * Interceptor para logar requisições/respostas (apenas debug)
     */
    private ClientHttpRequestInterceptor loggingInterceptor() {
        return (request, body, execution) -> {
            log.debug("AppyPay Request: {} {}", request.getMethod(), request.getURI());
            if (body.length > 0) {
                log.debug("AppyPay Request Body: {}", new String(body));
            }
            
            var response = execution.execute(request, body);
            
            log.debug("AppyPay Response: {}", response.getStatusCode());
            
            return response;
        };
    }
}
