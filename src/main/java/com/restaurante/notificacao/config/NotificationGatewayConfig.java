package com.restaurante.notificacao.config;

import com.restaurante.notificacao.gateway.SmsGateway;
import com.restaurante.notificacao.gateway.telcosms.TelcoSmsGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuração dos gateways de notificação
 * 
 * SOLID: Dependency Injection + Strategy Pattern
 * - Permite configurar qual gateway usar via Spring profiles ou properties
 * - Facilita testes (pode injetar mock do gateway)
 * - Extensível: adicionar novos gateways sem modificar código existente
 * 
 * Exemplo de uso:
 * - Produção: TelcoSmsGateway
 * - Testes: MockSmsGateway
 * - Futuro: TwilioGateway, AwsSnsGateway, etc.
 */
@Configuration
@lombok.RequiredArgsConstructor
public class NotificationGatewayConfig {
    
    private final com.restaurante.notificacao.gateway.telcosms.TelcoSmsProperties telcoSmsProperties;

    /**
     * RestTemplate dedicado para notificações SMS.
     * Configura timeouts curtos para não travar a aplicação em caso de lentidão do gateway.
     */
    @Bean(name = "smsRestTemplate")
    public org.springframework.web.client.RestTemplate smsRestTemplate(org.springframework.boot.web.client.RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(java.time.Duration.ofMillis(5000)) // 5s conexão
            .setReadTimeout(java.time.Duration.ofMillis(telcoSmsProperties.getTimeoutMs()))
            .build();
    }

    
    /**
     * Gateway SMS padrão: TelcoSMS
     * 
     * Pode ser substituído criando outro bean com @Primary
     * ou usando @Qualifier("outro-gateway") na injeção
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(SmsGateway.class)
    public SmsGateway defaultSmsGateway(TelcoSmsGateway telcoSmsGateway) {
        return telcoSmsGateway;
    }
    
    // Exemplo: adicionar outros gateways no futuro
    /*
    @Bean
    @ConditionalOnProperty(name = "app.notification.provider", havingValue = "twilio")
    public SmsGateway twilioSmsGateway(TwilioProperties properties) {
        return new TwilioGateway(properties);
    }
    
    @Bean
    @ConditionalOnProperty(name = "app.notification.provider", havingValue = "aws-sns")
    public SmsGateway awsSmsGateway(AwsSnsProperties properties) {
        return new AwsSnsGateway(properties);
    }
    */
}
