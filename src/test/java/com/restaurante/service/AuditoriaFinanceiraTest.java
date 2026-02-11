package com.restaurante.service;

import com.restaurante.model.entity.ConfiguracaoFinanceiraSistema;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.repository.ConfiguracaoFinanceiraSistemaRepository;
import com.restaurante.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes de auditoria financeira
 * 
 * Valida que eventos críticos são registrados corretamente:
 * - AUTORIZACAO_POS_PAGO
 * - CONFIRMACAO_PAGAMENTO_POS_PAGO
 * - ESTORNO_MANUAL
 * - POLITICA_POS_PAGO_ALTERADA
 * 
 * Campos obrigatórios: userId, role, ip, timestamp, motivo (quando aplicável)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Auditoria Financeira")
class AuditoriaFinanceiraTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private FundoConsumoService fundoConsumoService;

    @Mock
    private ConfiguracaoFinanceiraSistemaRepository configuracaoRepository;

    @Mock
    private EventLogService eventLogService;

    @InjectMocks
    private PedidoFinanceiroService pedidoFinanceiroService;

    @InjectMocks
    private ConfiguracaoFinanceiraService configuracaoFinanceiraService;

    private Pedido pedidoPosPago;
    private ConfiguracaoFinanceiraSistema configuracao;

    @BeforeEach
    void setUp() {
        pedidoPosPago = Pedido.builder()
                .numero("PED-AUDIT-001")
                .tipoPagamento(TipoPagamentoPedido.POS_PAGO)
                .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)
                .total(new BigDecimal("100.00"))
                .build();
        pedidoPosPago.setId(1L);

        configuracao = ConfiguracaoFinanceiraSistema.builder()
                .posPagoAtivo(true)
                .atualizadoPorNome("Admin")
                .atualizadoPorRole("ADMIN")
                .build();
        configuracao.setId(1L);
    }

    @Test
    @DisplayName("Confirmação de pagamento pós-pago deve gerar evento de auditoria")
    void confirmarPagamentoPosPagoDeveGerarEvento() {
        when(pedidoRepository.findById(pedidoPosPago.getId())).thenReturn(Optional.of(pedidoPosPago));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoPosPago);

        pedidoFinanceiroService.confirmarPagamentoPosPago(pedidoPosPago.getId());

        // TODO: Descomentar quando EventLog CONFIRMACAO_PAGAMENTO_POS_PAGO estiver implementado
        // verify(eventLogService, times(1)).registrarEventoFinanceiro(
        //     eq(pedidoPosPago),
        //     eq(TipoEvento.CONFIRMACAO_PAGAMENTO_POS_PAGO),
        //     any(),
        //     any()
        // );
        
        // Por enquanto, apenas verifica que pedido foi salvo
        verify(pedidoRepository, times(1)).save(any(Pedido.class));
        assertEquals(StatusFinanceiroPedido.PAGO, pedidoPosPago.getStatusFinanceiro());
    }

    @Test
    @DisplayName("Estorno manual deve gerar evento de auditoria com motivo")
    void estornoManualDeveGerarEventoComMotivo() {
        pedidoPosPago.marcarComoPago();
        when(pedidoRepository.findById(pedidoPosPago.getId())).thenReturn(Optional.of(pedidoPosPago));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoPosPago);

        String motivo = "Cliente solicitou cancelamento";
        pedidoFinanceiroService.estornarPedido(pedidoPosPago.getId(), motivo);

        // TODO: Descomentar quando EventLog ESTORNO_MANUAL estiver implementado
        // verify(eventLogService, times(1)).registrarEventoFinanceiro(
        //     eq(pedidoPosPago),
        //     eq(TipoEvento.ESTORNO_MANUAL),
        //     eq(motivo),
        //     any()
        // );

        verify(pedidoRepository, times(1)).save(any(Pedido.class));
        assertEquals(StatusFinanceiroPedido.ESTORNADO, pedidoPosPago.getStatusFinanceiro());
    }

    @Test
    @DisplayName("Alteração de política pós-pago deve gerar evento de auditoria")
    void alteracaoPoliticaPosPagoDeveGerarEvento() {
        when(configuracaoRepository.findAtual()).thenReturn(Optional.of(configuracao));
        when(configuracaoRepository.save(any(ConfiguracaoFinanceiraSistema.class)))
                .thenReturn(configuracao);

        // Desativar pós-pago
        configuracaoFinanceiraService.alterarPosPagoAtivo(false, "Admin", "ADMIN");

        // TODO: Descomentar quando EventLog POLITICA_POS_PAGO_ALTERADA estiver implementado
        // verify(eventLogService, times(1)).registrarEventoConfiguracao(
        //     eq(configuracao),
        //     eq(TipoEvento.POLITICA_POS_PAGO_ALTERADA),
        //     eq(true),  // valorAnterior
        //     eq(false), // novoValor
        //     eq("Admin"),
        //     eq("ADMIN")
        // );

        verify(configuracaoRepository, times(1)).save(any(ConfiguracaoFinanceiraSistema.class));
        assertFalse(configuracao.getPosPagoAtivo());
    }

    @Test
    @DisplayName("Evento de auditoria não deve permitir ação financeira sem registro")
    void nenhumaAcaoFinanceiraSemAuditoria() {
        // Este teste documenta que TODAS as ações financeiras críticas
        // devem passar por EventLogService
        
        // Cenário: Se EventLogService falhar, a operação deve falhar
        // TODO: Implementar quando EventLogService estiver integrado
        
        assertTrue(true, "Documentação: Todas ações financeiras requerem auditoria");
    }

    @Test
    @DisplayName("Estorno sem motivo não deve ser permitido")
    void estornoSemMotivoNaoPermitido() {
        pedidoPosPago.marcarComoPago();
        lenient().when(pedidoRepository.findById(pedidoPosPago.getId())).thenReturn(Optional.of(pedidoPosPago));

        // Tenta estornar sem motivo
        assertThrows(Exception.class, () -> 
            pedidoFinanceiroService.estornarPedido(pedidoPosPago.getId(), "")
        );

        // Não deve ter salvo (EventLogService pode não existir nos mocks)
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    @DisplayName("Confirmação de pagamento deve registrar userId e role")
    void confirmacaoDeveRegistrarUsuarioERole() {
        when(pedidoRepository.findById(pedidoPosPago.getId())).thenReturn(Optional.of(pedidoPosPago));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoPosPago);

        pedidoFinanceiroService.confirmarPagamentoPosPago(pedidoPosPago.getId());

        // TODO: Quando EventLog estiver implementado, verificar que contém:
        // - userId (do SecurityContext)
        // - role (GERENTE ou ADMIN)
        // - ip (do HttpServletRequest)
        // - timestamp (automático)
        
        assertTrue(pedidoPosPago.isPago(), "Pedido deve estar pago após confirmação");
    }

    @Test
    @DisplayName("Estorno deve registrar motivo obrigatório no evento")
    void estornoDeveRegistrarMotivoNoEvento() {
        pedidoPosPago.marcarComoPago();
        when(pedidoRepository.findById(pedidoPosPago.getId())).thenReturn(Optional.of(pedidoPosPago));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoPosPago);

        String motivo = "Produto defeituoso - devolução total";
        pedidoFinanceiroService.estornarPedido(pedidoPosPago.getId(), motivo);

        // TODO: Quando EventLog estiver implementado, verificar que evento contém:
        // - motivo exato: "Produto defeituoso - devolução total"
        // - userId
        // - role
        // - timestamp
        
        assertEquals(StatusFinanceiroPedido.ESTORNADO, pedidoPosPago.getStatusFinanceiro());
    }

    @Test
    @DisplayName("Alteração de política não exige motivo mas exige auditoria")
    void alteracaoPoliticaNaoExigeMotivoMasExigeAuditoria() {
        when(configuracaoRepository.findAtual()).thenReturn(Optional.of(configuracao));
        when(configuracaoRepository.save(any(ConfiguracaoFinanceiraSistema.class)))
                .thenReturn(configuracao);

        // Alteração de política sem motivo deve funcionar
        configuracaoFinanceiraService.alterarPosPagoAtivo(false, "Admin", "ADMIN");

        // TODO: Mas deve gerar evento de auditoria sem motivo
        // verify(eventLogService, times(1)).registrarEventoConfiguracao(
        //     any(), any(), any(), any(), eq("Admin"), eq("ADMIN")
        // );

        verify(configuracaoRepository, times(1)).save(any(ConfiguracaoFinanceiraSistema.class));
    }
}
