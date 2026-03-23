package com.restaurante.notificacao.controller;

import com.restaurante.notificacao.service.NotificacaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller para gerenciamento de notificações
 */
@RestController
@RequestMapping("/api/notificacoes")
public class NotificacaoController {
    
    private final NotificacaoService notificacaoService;
    
    public NotificacaoController(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }
    
    /**
     * Envia SMS genérico
     */
    @PostMapping("/sms")
    public ResponseEntity<?> enviarSms(@RequestBody Map<String, String> request) {
        String telefone = request.get("telefone");
        String mensagem = request.get("mensagem");
        String contexto = request.getOrDefault("contexto", "GENERICO");
        
        boolean enviado = notificacaoService.enviarSms(telefone, mensagem, contexto);
        
        if (enviado) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "SMS enviado com sucesso"
            ));
        } else {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Falha ao enviar SMS"
            ));
        }
    }
    
    /**
     * Envia OTP via SMS
     */
    @PostMapping("/otp")
    public ResponseEntity<?> enviarOtp(@RequestBody Map<String, String> request) {
        String telefone = request.get("telefone");
        String codigo = request.get("codigo");
        
        boolean enviado = notificacaoService.enviarOtp(telefone, codigo);
        
        if (enviado) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "OTP enviado com sucesso"
            ));
        } else {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Falha ao enviar OTP"
            ));
        }
    }
    
    /**
     * Envia notificação de recarga confirmada
     */
    @PostMapping("/recarga-confirmada")
    public ResponseEntity<?> notificarRecargaConfirmada(@RequestBody Map<String, Object> request) {
        String telefone = (String) request.get("telefone");
        double valor = ((Number) request.get("valor")).doubleValue();
        String metodoPagamento = (String) request.get("metodoPagamento");
        
        notificacaoService.enviarNotificacaoRecargaConfirmada(telefone, valor, metodoPagamento);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Notificação de recarga enviada"
        ));
    }
    
    /**
     * Envia notificação de pedido criado
     */
    @PostMapping("/pedido-criado")
    public ResponseEntity<?> notificarPedidoCriado(@RequestBody Map<String, Object> request) {
        String telefone = (String) request.get("telefone");
        String numeroPedido = (String) request.get("numeroPedido");
        double total = ((Number) request.get("total")).doubleValue();
        String itens = (String) request.getOrDefault("itens", "Itens diversos");
        
        notificacaoService.enviarNotificacaoPedidoCriado(telefone, numeroPedido, total, itens);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Notificação de pedido criado enviada"
        ));
    }
    
    /**
     * Envia notificação de pedido pronto
     */
    @PostMapping("/pedido-pronto")
    public ResponseEntity<?> notificarPedidoPronto(@RequestBody Map<String, String> request) {
        String telefone = request.get("telefone");
        String numeroPedido = request.get("numeroPedido");
        String itens = (String) request.getOrDefault("itens", "Itens diversos");
        
        notificacaoService.enviarNotificacaoPedidoPronto(telefone, numeroPedido, itens);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Notificação de pedido pronto enviada"
        ));
    }
    
    /**
     * Envia notificação de referência bancária
     */
    @PostMapping("/referencia-bancaria")
    public ResponseEntity<?> notificarReferenciaBancaria(@RequestBody Map<String, Object> request) {
        String telefone = (String) request.get("telefone");
        String entidade = (String) request.get("entidade");
        String referencia = (String) request.get("referencia");
        double valor = ((Number) request.get("valor")).doubleValue();
        
        notificacaoService.enviarNotificacaoReferenciaBancaria(telefone, entidade, referencia, valor);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Notificação de referência bancária enviada"
        ));
    }
    
    /**
     * Envia notificação de saldo insuficiente
     */
    @PostMapping("/saldo-insuficiente")
    public ResponseEntity<?> notificarSaldoInsuficiente(@RequestBody Map<String, Object> request) {
        String telefone = (String) request.get("telefone");
        double saldoAtual = ((Number) request.get("saldoAtual")).doubleValue();
        double valorNecessario = ((Number) request.get("valorNecessario")).doubleValue();
        
        notificacaoService.enviarNotificacaoSaldoInsuficiente(telefone, saldoAtual, valorNecessario);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Notificação de saldo insuficiente enviada"
        ));
    }
}
