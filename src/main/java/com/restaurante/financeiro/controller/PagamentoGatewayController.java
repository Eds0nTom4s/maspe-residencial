package com.restaurante.financeiro.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.service.PagamentoGatewayService;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.repository.ClienteRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Controller para integração com Gateway de Pagamento (AppyPay).
 * Suporta recargas de fundo e pagamentos de pedidos via GPO/REF.
 */
@RestController
@RequestMapping("/api/financeiro/pagamento")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Pagamento Gateway", description = "Integração com AppyPay (Multicaixa Express / Referência)")
public class PagamentoGatewayController {

    private final PagamentoGatewayService pagamentoService;
    private final ClienteRepository clienteRepository;

    /**
     * Solicita um pagamento de recarga para um cliente que ainda NÃO possui sessão ativa.
     * A sessão e o fundo serão criados automaticamente após a confirmação do pagamento.
     * 
     * POST /api/financeiro/pagamento/recarga-nova-sessao
     */
    @PostMapping("/recarga-nova-sessao")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Solicitar recarga para abrir nova sessão (Cliente via App)")
    public ResponseEntity<ApiResponse<Pagamento>> solicitarRecargaNovaSessao(
            @RequestParam BigDecimal valor,
            @RequestParam MetodoPagamentoAppyPay metodo,
            @RequestHeader(value = "X-Forwarded-For", required = false) String ip) {
        
        String telefone = getUsuarioLogado();
        log.info("Cliente {} solicitando recarga de {} para nova sessão via {}", telefone, valor, metodo);
        
        com.restaurante.model.entity.Cliente cliente = clienteRepository.findByTelefone(telefone)
                .orElseThrow(() -> new com.restaurante.exception.ResourceNotFoundException("Cliente não encontrado"));

        Pagamento pagamento = pagamentoService.criarPagamentoRecargaSemSessao(
                cliente.getId(),
                valor,
                metodo,
                telefone,
                "CLIENTE",
                ip != null ? ip : "127.0.0.1",
                telefone // telefone for push
        );

        return ResponseEntity.ok(ApiResponse.success("Pagamento de recarga solicitado com sucesso", pagamento));
    }

    /**
     * Solicita um pagamento de recarga para a sessão ativa do cliente.
     * 
     * POST /api/financeiro/pagamento/recarga-sessao-ativa
     */
    @PostMapping("/recarga-sessao-ativa")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Solicitar recarga para sessão ativa (Cliente via App)")
    public ResponseEntity<ApiResponse<Pagamento>> solicitarRecargaSessaoAtiva(
            @RequestParam Long fundoId,
            @RequestParam BigDecimal valor,
            @RequestParam MetodoPagamentoAppyPay metodo,
            @RequestHeader(value = "X-Forwarded-For", required = false) String ip) {
        
        String telefone = getUsuarioLogado();
        log.info("Cliente {} solicitando recarga de {} para fundo {} via {}", telefone, valor, fundoId, metodo);

        Pagamento pagamento = pagamentoService.criarPagamentoRecargaFundo(
                fundoId,
                valor,
                metodo,
                telefone, // usuario (same as phone/auth)
                "CLIENTE", // role
                ip != null ? ip : "127.0.0.1",
                telefone // telefone for push
        );

        return ResponseEntity.ok(ApiResponse.success("Pagamento de recarga solicitado com sucesso", pagamento));
    }

    private String getUsuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
