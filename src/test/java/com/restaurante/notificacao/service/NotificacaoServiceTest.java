package com.restaurante.notificacao.service;

import com.restaurante.notificacao.gateway.SmsGateway;
import com.restaurante.notificacao.gateway.SmsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para NotificacaoService
 * Verifica integração com SmsGateway (abstração)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificacaoService - Testes de Notificações SMS")
class NotificacaoServiceTest {

    @Mock
    private SmsGateway smsGateway;

    @InjectMocks
    private NotificacaoService notificacaoService;

    private String telefone;

    @BeforeEach
    void setUp() {
        telefone = "+244925813939";
        when(smsGateway.getProviderName()).thenReturn("MockGateway");
    }

    @Test
    @DisplayName("Deve enviar OTP com sucesso")
    void deveEnviarOtpComSucesso() {
        // Arrange
        String codigo = "1234";
        when(smsGateway.sendSms(anyString(), anyString()))
                .thenReturn(SmsResponse.success("SMS-123"));

        // Act
        boolean enviado = notificacaoService.enviarOtp(telefone, codigo);

        // Assert
        assertTrue(enviado);
        verify(smsGateway, times(1)).sendSms(
                eq(telefone), 
                contains(codigo)
        );
    }

    @Test
    @DisplayName("Deve enviar notificação de recarga confirmada")
    void deveEnviarNotificacaoRecargaConfirmada() {
        // Arrange
        double valor = 150.00;
        String metodoPagamento = "GPO";
        when(smsGateway.sendSms(anyString(), anyString()))
                .thenReturn(SmsResponse.success("SMS-456"));

        // Act
        boolean enviado = notificacaoService.enviarNotificacaoRecargaConfirmada(
                telefone, valor, metodoPagamento);

        // Assert
        assertTrue(enviado);
        verify(smsGateway, times(1)).sendSms(
                eq(telefone), 
                contains("Recarga confirmada")
        );
    }

    @Test
    @DisplayName("Deve enviar notificação de pedido criado")
    void deveEnviarNotificacaoPedidoCriado() {
        // Arrange
        String numeroPedido = "001";
        double total = 85.50;
        when(smsGateway.sendSms(anyString(), anyString()))
                .thenReturn(SmsResponse.success("SMS-789"));

        // Act
        boolean enviado = notificacaoService.enviarNotificacaoPedidoCriado(
                telefone, numeroPedido, total);

        // Assert
        assertTrue(enviado);
        verify(smsGateway, times(1)).sendSms(
                eq(telefone), 
                contains(numeroPedido)
        );
    }

    @Test
    @DisplayName("Deve enviar notificação de pedido pronto")
    void deveEnviarNotificacaoPedidoPronto() {
        // Arrange
        String numeroPedido = "001";
        when(smsGateway.sendSms(anyString(), anyString()))
                .thenReturn(SmsResponse.success("SMS-ABC"));

        // Act
        boolean enviado = notificacaoService.enviarNotificacaoPedidoPronto(
                telefone, numeroPedido);

        // Assert
        assertTrue(enviado);
        verify(smsGateway, times(1)).sendSms(
                eq(telefone), 
                contains("pronto")
        );
    }

    @Test
    @DisplayName("Deve enviar notificação de referência bancária")
    void deveEnviarNotificacaoReferenciaBancaria() {
        // Arrange
        String entidade = "12345";
        String referencia = "987654321";
        double valor = 200.00;
        when(smsGateway.sendSms(anyString(), anyString()))
                .thenReturn(SmsResponse.success("SMS-DEF"));

        // Act
        boolean enviado = notificacaoService.enviarNotificacaoReferenciaBancaria(
                telefone, entidade, referencia, valor);

        // Assert
        assertTrue(enviado);
        verify(smsGateway, times(1)).sendSms(
                eq(telefone), 
                contains(referencia)
        );
    }

    @Test
    @DisplayName("Deve enviar notificação de saldo insuficiente")
    void deveEnviarNotificacaoSaldoInsuficiente() {
        // Arrange
        double saldoAtual = 50.00;
        double valorNecessario = 120.00;
        when(smsGateway.sendSms(anyString(), anyString()))
                .thenReturn(SmsResponse.success("SMS-GHI"));

        // Act
        boolean enviado = notificacaoService.enviarNotificacaoSaldoInsuficiente(
                telefone, saldoAtual, valorNecessario);

        // Assert
        assertTrue(enviado);
        verify(smsGateway, times(1)).sendSms(
                eq(telefone), 
                contains("insuficiente")
        );
    }

    @Test
    @DisplayName("Deve retornar false quando gateway falhar")
    void deveRetornarFalseQuandoGatewayFalhar() {
        // Arrange
        when(smsGateway.sendSms(anyString(), anyString()))
                .thenReturn(SmsResponse.error("Falha no envio"));

        // Act
        boolean enviado = notificacaoService.enviarOtp(telefone, "1234");

        // Assert
        assertFalse(enviado);
        verify(smsGateway, times(1)).sendSms(anyString(), anyString());
    }

    @Test
    @DisplayName("Deve capturar exceção e retornar false")
    void deveCapturarExcecaoERetornarFalse() {
        // Arrange
        when(smsGateway.sendSms(anyString(), anyString()))
                .thenThrow(new RuntimeException("Erro de conexão"));

        // Act
        boolean enviado = notificacaoService.enviarOtp(telefone, "1234");

        // Assert
        assertFalse(enviado);
        verify(smsGateway, times(1)).sendSms(anyString(), anyString());
    }

    @Test
    @DisplayName("Deve realizar retry com backoff exponencial")
    void deveRealizarRetryComBackoffExponencial() {
        // Arrange
        when(smsGateway.sendSms(anyString(), anyString()))
                .thenReturn(SmsResponse.error("Falha 1"))
                .thenReturn(SmsResponse.error("Falha 2"))
                .thenReturn(SmsResponse.success("SMS-OK"));

        // Act
        boolean enviado = notificacaoService.enviarComRetry(
                telefone, "Teste retry", "TESTE", 3);

        // Assert
        assertTrue(enviado);
        verify(smsGateway, times(3)).sendSms(anyString(), anyString());
    }

    @Test
    @DisplayName("Deve falhar após esgotar todas as tentativas de retry")
    void deveFalharAposEsgotarTodasAsTentativasDeRetry() {
        // Arrange
        when(smsGateway.sendSms(anyString(), anyString()))
                .thenReturn(SmsResponse.error("Falha persistente"));

        // Act
        boolean enviado = notificacaoService.enviarComRetry(
                telefone, "Teste retry", "TESTE", 3);

        // Assert
        assertFalse(enviado);
        verify(smsGateway, times(3)).sendSms(anyString(), anyString());
    }

    @Test
    @DisplayName("Deve enviar SMS genérico com contexto")
    void deveEnviarSmsGenericoComContexto() {
        // Arrange
        String mensagem = "Mensagem de teste";
        String contexto = "TESTE";
        when(smsGateway.sendSms(anyString(), anyString()))
                .thenReturn(SmsResponse.success("SMS-XYZ"));

        // Act
        boolean enviado = notificacaoService.enviarSms(telefone, mensagem, contexto);

        // Assert
        assertTrue(enviado);
        verify(smsGateway, times(1)).sendSms(telefone, mensagem);
    }
}
