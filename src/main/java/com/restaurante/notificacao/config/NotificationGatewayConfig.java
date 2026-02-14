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
public class NotificationGatewayConfig {
    
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
