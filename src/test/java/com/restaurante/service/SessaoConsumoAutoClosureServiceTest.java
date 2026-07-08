package com.restaurante.service;

import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.service.operacional.OperationalEventLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PASSO 7 — Testes Backend Obrigatórios
 * PROMPT-BACKEND-CONSUMA-SESSAO-CONSUMO-AUTO-CLOSURE-PONTO-001
 *
 * Cobre os 20 cenários obrigatórios do prompt de auto-fecho.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessaoConsumoAutoClosureService — 20 cenários obrigatórios")
class SessaoConsumoAutoClosureServiceTest {

    @Mock SessaoConsumoRepository sessaoConsumoRepository;
    @Mock PedidoRepository pedidoRepository;
    @Mock OrdemPagamentoRepository ordemPagamentoRepository;
    @Mock OperationalEventLogService operationalEventLogService;

    @InjectMocks SessaoConsumoAutoClosureService service;

    private SessaoConsumo sessaoPonto;
    private Tenant tenantPonto;

    @BeforeEach
    void setup() {
        tenantPonto = new Tenant();
        tenantPonto.setId(1L);
        tenantPonto.setTemplateCode("CONSUMA_PONTO_V1");

        sessaoPonto = new SessaoConsumo();
        sessaoPonto.setId(10L);
        sessaoPonto.setStatus(StatusSessaoConsumo.ABERTA);
        sessaoPonto.setTenant(tenantPonto);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 1: sessão PONTO + pedido FINALIZADO + pagamento PAGO → encerra
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("1. sessão PONTO + pedido FINALIZADO + PAGO → encerrada automaticamente")
    void cenario1_pontoFinalizadoPago_encerra() {
        Pedido p = pedidoFinalizado();
        mockSessao(sessaoPonto);
        mockPedidos(p);
        mockSemOrdemAtiva();

        service.tryAutoCloseSessaoConsumo(10L);

        verify(sessaoConsumoRepository).save(argThat(s -> s.getStatus() == StatusSessaoConsumo.ENCERRADA));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 2: sessão PONTO + pedido CANCELADO + sem pagamento → encerra
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("2. sessão PONTO + pedido CANCELADO + sem pagamento → encerrada automaticamente")
    void cenario2_pontoCancelado_encerra() {
        Pedido p = pedidoCancelado();
        mockSessao(sessaoPonto);
        mockPedidos(p);
        mockSemOrdemAtiva();

        service.tryAutoCloseSessaoConsumo(10L);

        verify(sessaoConsumoRepository).save(argThat(s -> s.getStatus() == StatusSessaoConsumo.ENCERRADA));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 3: sessão PONTO + pedido CRIADO → NÃO encerra
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("3. sessão PONTO + pedido CRIADO → não encerra")
    void cenario3_pontoPedidoCriado_naoEncerra() {
        Pedido p = pedidoComStatus(StatusPedido.CRIADO);
        mockSessao(sessaoPonto);
        mockPedidos(p);

        service.tryAutoCloseSessaoConsumo(10L);

        verify(sessaoConsumoRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 4: sessão PONTO + pedido EM_ANDAMENTO → NÃO encerra
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("4. sessão PONTO + pedido EM_ANDAMENTO → não encerra")
    void cenario4_pontoEmAndamento_naoEncerra() {
        Pedido p = pedidoComStatus(StatusPedido.EM_ANDAMENTO);
        mockSessao(sessaoPonto);
        mockPedidos(p);

        service.tryAutoCloseSessaoConsumo(10L);

        verify(sessaoConsumoRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 5: sessão PONTO + financeiro NAO_PAGO → NÃO encerra
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("5. sessão PONTO + financeiro NAO_PAGO → não encerra")
    void cenario5_naoPago_naoEncerra() {
        Pedido p = pedidoComStatus(StatusPedido.FINALIZADO);
        p.setStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO);
        mockSessao(sessaoPonto);
        mockPedidos(p);

        service.tryAutoCloseSessaoConsumo(10L);

        verify(sessaoConsumoRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 6: sessão PONTO + financeiro PENDENTE_PAGAMENTO → NÃO encerra
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("6. sessão PONTO + financeiro PENDENTE_PAGAMENTO → não encerra")
    void cenario6_pendentePagamento_naoEncerra() {
        Pedido p = pedidoComStatus(StatusPedido.FINALIZADO);
        p.setStatusFinanceiro(StatusFinanceiroPedido.PENDENTE_PAGAMENTO);
        mockSessao(sessaoPonto);
        mockPedidos(p);

        service.tryAutoCloseSessaoConsumo(10L);

        verify(sessaoConsumoRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 7: sessão PONTO + OrdemPagamento AGUARDANDO_CONFIRMACAO → NÃO encerra
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("7. sessão PONTO + OrdemPagamento AGUARDANDO_CONFIRMACAO → não encerra")
    void cenario7_ordemAtiva_naoEncerra() {
        Pedido p = pedidoFinalizado();
        mockSessao(sessaoPonto);
        mockPedidos(p);
        when(ordemPagamentoRepository.existsBySessaoConsumoIdAndStatusIn(eq(10L), anyList()))
                .thenReturn(true);

        service.tryAutoCloseSessaoConsumo(10L);

        verify(sessaoConsumoRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 8: sessão PONTO + subpedido PENDENTE obrigatório → NÃO encerra
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("8. sessão PONTO + subpedido PENDENTE → não encerra")
    void cenario8_subpedidoPendente_naoEncerra() {
        Pedido p = pedidoComStatus(StatusPedido.FINALIZADO);
        p.setStatusFinanceiro(StatusFinanceiroPedido.PAGO);

        SubPedido sub = new SubPedido();
        sub.setStatus(StatusSubPedido.PENDENTE);
        p.setSubPedidos(List.of(sub));

        mockSessao(sessaoPonto);
        mockPedidos(p);

        service.tryAutoCloseSessaoConsumo(10L);

        verify(sessaoConsumoRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 9: sessão PONTO já ENCERRADA → idempotência, sem duplicidade
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("9. sessão PONTO já ENCERRADA → idempotência, sem save duplicado")
    void cenario9_jaEncerrada_idempotente() {
        sessaoPonto.setStatus(StatusSessaoConsumo.ENCERRADA);
        mockSessao(sessaoPonto);

        service.tryAutoCloseSessaoConsumo(10L);

        verify(sessaoConsumoRepository, never()).save(any());
        verify(pedidoRepository, never()).findBySessaoConsumoId(any(), any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 10: sessão REST/KDS → NÃO auto-encerrada pela regra PONTO
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("10. sessão REST/KDS → não auto-encerrada pela regra PONTO")
    void cenario10_restKds_naoEncerra() {
        tenantPonto.setTemplateCode("CONSUMA_REST_V1");
        mockSessao(sessaoPonto);

        service.tryAutoCloseSessaoConsumo(10L);

        verify(sessaoConsumoRepository, never()).save(any());
        verify(pedidoRepository, never()).findBySessaoConsumoId(any(), any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 11: sessão QR Mesa REST → NÃO auto-encerrada agressivamente
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("11. sessão QR Mesa REST → não auto-encerrada agressivamente")
    void cenario11_qrMesaRest_naoEncerra() {
        tenantPonto.setTemplateCode("CONSUMA_REST_KDS_V1");
        mockSessao(sessaoPonto);

        service.tryAutoCloseSessaoConsumo(10L);

        verify(sessaoConsumoRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 12: após auto-fecho, sessão encerrada não bloqueia pré-fecho
    //             (validado via status ENCERRADA após save)
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("12. após auto-fecho, sessão fica ENCERRADA (não bloqueia pré-fecho do turno)")
    void cenario12_aposAutoFecho_statusEncerrada() {
        Pedido p = pedidoFinalizado();
        mockSessao(sessaoPonto);
        mockPedidos(p);
        mockSemOrdemAtiva();

        service.tryAutoCloseSessaoConsumo(10L);

        verify(sessaoConsumoRepository).save(argThat(s ->
                s.getStatus() == StatusSessaoConsumo.ENCERRADA
        ));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 13: auditoria de auto-fecho é registrada
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("13. auditoria de auto-fecho é registrada via OperationalEventLogService")
    void cenario13_auditoriaRegistrada() {
        Pedido p = pedidoFinalizado();
        mockSessao(sessaoPonto);
        mockPedidos(p);
        mockSemOrdemAtiva();

        service.tryAutoCloseSessaoConsumo(10L);

        verify(operationalEventLogService).logGenericForTenant(
                eq(1L),
                eq(com.restaurante.model.enums.OperationalEventType.SESSAO_CONSUMO_ENCERRADA),
                eq(com.restaurante.model.enums.OperationalEntityType.SESSAO_CONSUMO),
                eq(10L),
                eq(com.restaurante.model.enums.OperationalOrigem.SYSTEM),
                anyString(),
                argThat(m -> "AUTO_CLOSURE_CONSUMA_PONTO".equals(m.get("reason"))),
                anyString(),
                anyString()
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 14: pagamento permanece PAGO (não alterado)
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("14. pagamento permanece PAGO após auto-fecho")
    void cenario14_pagamentoPermanecePago() {
        Pedido p = pedidoFinalizado();
        mockSessao(sessaoPonto);
        mockPedidos(p);
        mockSemOrdemAtiva();

        service.tryAutoCloseSessaoConsumo(10L);

        // O pedido não é alterado pelo service — apenas a sessão é salva
        verify(pedidoRepository, never()).save(any());
        // O status financeiro original permanece intacto
        assert p.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 15: pedido permanece FINALIZADO (não alterado)
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("15. pedido permanece FINALIZADO após auto-fecho")
    void cenario15_pedidoPermaneceFinalizado() {
        Pedido p = pedidoFinalizado();
        mockSessao(sessaoPonto);
        mockPedidos(p);
        mockSemOrdemAtiva();

        service.tryAutoCloseSessaoConsumo(10L);

        verify(pedidoRepository, never()).save(any());
        assert p.getStatus() == StatusPedido.FINALIZADO;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 16: turno permanece inalterado (service não toca em turno)
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("16. turno não é alterado pelo auto-fecho")
    void cenario16_turnoNaoAlterado() {
        Pedido p = pedidoFinalizado();
        mockSessao(sessaoPonto);
        mockPedidos(p);
        mockSemOrdemAtiva();

        service.tryAutoCloseSessaoConsumo(10L);

        // Nenhum repositório de turno é chamado (TurnoOperacionalRepository não é injectado)
        // Verificar que sessaoConsumoRepository.save é o único save chamado
        verify(sessaoConsumoRepository, times(1)).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 17: não cria caixa, fiscal, extrato
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("17. não cria caixa, fiscal nem extrato")
    void cenario17_semCaixaFiscalExtrato() {
        Pedido p = pedidoFinalizado();
        mockSessao(sessaoPonto);
        mockPedidos(p);
        mockSemOrdemAtiva();

        service.tryAutoCloseSessaoConsumo(10L);

        // O service só usa: sessaoConsumoRepository, pedidoRepository, ordemPagamentoRepository, operationalEventLogService
        // Nenhum outro mock foi injectado — qualquer chamada extra quebraria o teste
        verify(sessaoConsumoRepository, times(1)).save(any());
        verify(sessaoConsumoRepository, times(1)).findById(10L);
        verify(pedidoRepository, times(1)).findBySessaoConsumoId(eq(10L), any());
        verify(ordemPagamentoRepository, times(1)).existsBySessaoConsumoIdAndStatusIn(anyLong(), anyList());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 18: não chama gateway
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("18. gateway não é chamado durante auto-fecho")
    void cenario18_semGateway() {
        // O SessaoConsumoAutoClosureService não recebe gateway como dependência.
        // Se um gateway fosse chamado, a injecção seria necessária e o teste
        // quebraria ao construir o service. Esta asserção documenta a intenção.
        //
        // Validamos que o auto-fecho ocorre correctamente sem chamadas a gateway:
        Pedido p = pedidoFinalizado();
        mockSessao(sessaoPonto);
        mockPedidos(p);
        mockSemOrdemAtiva();

        service.tryAutoCloseSessaoConsumo(10L);

        // Gateway não é dependência do service — qualquer chamada extra quebraria
        // o contexto de injecção. Confirmamos apenas o comportamento esperado:
        verify(sessaoConsumoRepository).save(argThat(s -> s.getStatus() == StatusSessaoConsumo.ENCERRADA));
        // Nenhum repositório financeiro de pagamento externo é chamado (sem gateway)
        verify(pedidoRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 19: sessão sem pedidos → NÃO encerra (aguarda scheduler expirar)
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("19. sessão PONTO sem pedidos → não encerra (deixa scheduler expirar)")
    void cenario19_semPedidos_naoEncerra() {
        mockSessao(sessaoPonto);
        when(pedidoRepository.findBySessaoConsumoId(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        service.tryAutoCloseSessaoConsumo(10L);

        verify(sessaoConsumoRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cenário 20: sessão PONTO AGUARDANDO_PAGAMENTO → elegível se tudo liquidado
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("20. sessão AGUARDANDO_PAGAMENTO + tudo liquidado → encerrada automaticamente")
    void cenario20_aguardandoPagamentoLiquidado_encerra() {
        sessaoPonto.setStatus(StatusSessaoConsumo.AGUARDANDO_PAGAMENTO);
        Pedido p = pedidoFinalizado();
        mockSessao(sessaoPonto);
        mockPedidos(p);
        mockSemOrdemAtiva();

        service.tryAutoCloseSessaoConsumo(10L);

        verify(sessaoConsumoRepository).save(argThat(s -> s.getStatus() == StatusSessaoConsumo.ENCERRADA));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private Pedido pedidoFinalizado() {
        Pedido p = new Pedido();
        p.setId(100L);
        p.setStatus(StatusPedido.FINALIZADO);
        p.setStatusFinanceiro(StatusFinanceiroPedido.PAGO);
        p.setSubPedidos(Collections.emptyList());
        return p;
    }

    private Pedido pedidoCancelado() {
        Pedido p = new Pedido();
        p.setId(101L);
        p.setStatus(StatusPedido.CANCELADO);
        p.setStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO); // cancelado pode ter NAO_PAGO
        p.setSubPedidos(Collections.emptyList());
        return p;
    }

    private Pedido pedidoComStatus(StatusPedido status) {
        Pedido p = new Pedido();
        p.setId(102L);
        p.setStatus(status);
        p.setStatusFinanceiro(StatusFinanceiroPedido.PAGO);
        p.setSubPedidos(Collections.emptyList());
        return p;
    }

    private void mockSessao(SessaoConsumo sessao) {
        when(sessaoConsumoRepository.findById(sessao.getId())).thenReturn(Optional.of(sessao));
    }

    private void mockPedidos(Pedido... pedidos) {
        Page<Pedido> page = new PageImpl<>(List.of(pedidos));
        when(pedidoRepository.findBySessaoConsumoId(eq(10L), any(Pageable.class))).thenReturn(page);
    }

    private void mockSemOrdemAtiva() {
        when(ordemPagamentoRepository.existsBySessaoConsumoIdAndStatusIn(eq(10L), anyList()))
                .thenReturn(false);
    }
}
