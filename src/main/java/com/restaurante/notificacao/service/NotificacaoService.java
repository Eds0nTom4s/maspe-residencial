package com.restaurante.notificacao.service;

import com.restaurante.notificacao.gateway.SmsGateway;
import com.restaurante.notificacao.gateway.SmsResponse;
import org.springframework.scheduling.annotation.Async;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Serviço de notificações do sistema
 * Responsável por enviar notificações via SMS, e-mail, push, etc.
 * 
 * SOLID: Dependency Inversion Principle
 * - Depende de abstração (SmsGateway) ao invés de implementação concreta
 * - Permite trocar provedor SMS sem modificar este service
 */
@Service
public class NotificacaoService {
    
    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);
    
    private final SmsGateway smsGateway;
    
    public NotificacaoService(SmsGateway smsGateway) {
        this.smsGateway = smsGateway;
        log.info("NotificacaoService inicializado com gateway: {}", smsGateway.getProviderName());
    }
    
    /**
     * Envia notificação de OTP para autenticação
     */
    public boolean enviarOtp(String telefone, String codigo) {
        String mensagem = String.format(
            "Seu código de verificação é: %s\n" +
            "Válido por 5 minutos.\n" +
            "Sistema de Restauração",
            codigo
        );
        
        return enviarSms(telefone, mensagem, "OTP");
    }
    
    /**
     * Envia notificação de recarga de fundo confirmada
     */
    @Async
    public void enviarNotificacaoRecargaConfirmada(String telefone, double valor, String metodoPagamento) {
        String mensagem = String.format(
            "Recarga confirmada!\n" +
            "Valor: Kz %.2f\n" +
            "Método: %s\n" +
            "Sistema de Restauração",
            valor,
            metodoPagamento
        );
        
        enviarSms(telefone, mensagem, "RECARGA_CONFIRMADA");
    }
    
    /**
     * Envia notificação quando uma sessão de consumo é criada (mesa/quarto aberto)
     */
    @Async
    public void enviarNotificacaoSessaoCriada(String telefone, String referenciaMesa, String qrCodeSessao) {
        String mensagem = String.format(
            "Bem-vindo! A sua conta em %s foi aberta.\n" +
            "Seu TOKEN/QR Único: %s\n" +
            "Acompanhe o consumo e pague em: https://app.restaurante.com/s/%s\n" +
            "Sistema de Restauração",
            referenciaMesa != null ? "mesa/quarto (" + referenciaMesa + ")" : "balcão",
            qrCodeSessao,
            qrCodeSessao
        );
        
        enviarSms(telefone, mensagem, "SESSAO_CRIADA");
    }
    
    /**
     * Envia notificação de pedido criado
     */
    @Async
    public void enviarNotificacaoPedidoCriado(String telefone, String numeroPedido, double total, String itens) {
        String mensagem = String.format(
            "Pedido #%s criado com sucesso!\n" +
            "Itens: %s\n" +
            "Total: Kz %.2f\n" +
            "Aguardando preparação.\n" +
            "Sistema de Restauração",
            numeroPedido,
            itens,
            total
        );
        
        enviarSms(telefone, mensagem, "PEDIDO_CRIADO");
    }
    
    /**
     * Envia notificação de pedido pronto
     */
    @Async
    public void enviarNotificacaoPedidoPronto(String telefone, String numeroPedido, String itens) {
        String mensagem = String.format(
            "Seu pedido #%s está pronto! \uD83C\uDF74\n" +
            "Item(ns): %s\n" +
            "Dirija-se ao balcão de retirada.\n" +
            "Sistema de Restauração",
            numeroPedido,
            itens
        );
        
        enviarSms(telefone, mensagem, "PEDIDO_PRONTO");
    }
    
    /**
     * Envia notificação de referência bancária gerada
     */
    @Async
    public void enviarNotificacaoReferenciaBancaria(String telefone, String entidade, String referencia, double valor) {
        String mensagem = String.format(
            "Referência Multicaixa gerada:\n" +
            "Entidade: %s\n" +
            "Referência: %s\n" +
            "Valor: Kz %.2f\n" +
            "Válido por 24h.\n" +
            "Sistema de Restauração",
            entidade,
            referencia,
            valor
        );
        
        enviarSms(telefone, mensagem, "REFERENCIA_BANCARIA");
    }
    
    /**
     * Envia notificação de saldo insuficiente
     */
    @Async
    public void enviarNotificacaoSaldoInsuficiente(String telefone, double saldoAtual, double valorNecessario) {
        String mensagem = String.format(
            "Saldo insuficiente!\n" +
            "Saldo atual: Kz %.2f\n" +
            "Necessário: Kz %.2f\n" +
            "Recarregue seu fundo.\n" +
            "Sistema de Restauração",
            saldoAtual,
            valorNecessario
        );
        
        enviarSms(telefone, mensagem, "SALDO_INSUFICIENTE");
    }
    
    /**
     * Envia notificação quando o fundo é usado por terceiros para pagar um pedido
     */
    @Async
    public void enviarNotificacaoUsoFundoPorTerceiros(String telefone, String numeroPedido, double valor, String nomePagador) {
        String mensagem = String.format(
            "Seu fundo foi usado!\n" +
            "Valor: Kz %.2f\n" +
            "Pedido: #%s\n" +
            "Utilizado por: %s\n" +
            "Sistema de Restauração",
            valor,
            numeroPedido,
            nomePagador != null ? nomePagador : "Usuário anónimo"
        );
        
        enviarSms(telefone, mensagem, "USO_FUNDO_TERCEIROS");
    }
    
    /**
     * Método genérico para enviar SMS
     * Agnóstico de provedor (usa abstração SmsGateway)
     */
    public boolean enviarSms(String telefone, String mensagem, String contexto) {
        try {
            log.info("Enviando notificação SMS [{}] para {} via {}", 
                contexto, telefone, smsGateway.getProviderName());
            
            SmsResponse response = smsGateway.sendSms(telefone, mensagem);
            
            if (response.isSuccess()) {
                log.info("Notificação [{}] enviada com sucesso - ID: {}", contexto, response.getMessageId());
                return true;
            } else {
                log.warn("Falha ao enviar notificação [{}]: {}", contexto, response.getMessage());
                return false;
            }
            
        } catch (Exception e) {
            log.error("Erro ao enviar notificação [{}] para {}: {}", contexto, telefone, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Envia notificação com retry automático
     */
    public boolean enviarComRetry(String telefone, String mensagem, String contexto, int maxTentativas) {
        for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
            boolean sucesso = enviarSms(telefone, mensagem, contexto);
            
            if (sucesso) {
                return true;
            }
            
            if (tentativa < maxTentativas) {
                log.warn("Tentativa {}/{} falhou. Tentando novamente...", tentativa, maxTentativas);
                try {
                    Thread.sleep(2000 * tentativa); // Backoff exponencial
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.error("Todas as {} tentativas de envio falharam para {}", maxTentativas, telefone);
        return false;
    }
}
