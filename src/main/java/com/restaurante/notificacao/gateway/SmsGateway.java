package com.restaurante.notificacao.gateway;

/**
 * Interface genérica para gateways de envio de SMS
 * 
 * Permite desacoplar a lógica de negócio do provedor específico de SMS.
 * Implementações: TelcoSMS, Twilio, AWS SNS, etc.
 * 
 * SOLID: Dependency Inversion Principle
 * - Classes de alto nível (NotificacaoService) dependem de abstrações
 * - Classes de baixo nível (TelcoSmsGateway) implementam abstrações
 */
public interface SmsGateway {
    
    /**
     * Envia SMS para o número especificado
     * 
     * @param phoneNumber Número de telefone (formato internacional)
     * @param message Mensagem a ser enviada
     * @return Response do envio
     */
    SmsResponse sendSms(String phoneNumber, String message);
    
    /**
     * Verifica se o gateway está em modo mock
     * 
     * @return true se estiver em modo simulação
     */
    boolean isMockMode();
    
    /**
     * Obtém o nome do provedor
     * 
     * @return Nome do gateway (ex: "TelcoSMS", "Twilio")
     */
    String getProviderName();
}
