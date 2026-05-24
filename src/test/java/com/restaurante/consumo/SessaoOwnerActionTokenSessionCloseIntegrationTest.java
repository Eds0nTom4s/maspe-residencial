package com.restaurante.consumo;

import com.restaurante.consumo.participante.service.SessaoOwnerActionTokenService;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.repository.*;
import com.restaurante.service.*;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.notificacao.service.NotificacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SessaoConsumoService — Revogação de Tokens de Ação no Fechamento")
class SessaoOwnerActionTokenSessionCloseIntegrationTest {

    @Mock SessaoConsumoRepository sessaoConsumoRepository;
    @Mock MesaRepository mesaRepository;
    @Mock ClienteService clienteService;
    @Mock AtendenteRepository atendenteRepository;
    @Mock UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Mock FundoConsumoService fundoConsumoService;
    @Mock PedidoFinanceiroService pedidoFinanceiroService;
    @Mock PedidoRepository pedidoRepository;
    @Mock QrCodeService qrCodeService;
    @Mock NotificacaoService notificacaoService;
    @Mock InstituicaoRepository instituicaoRepository;
    @Mock EventoSessaoRepository eventoSessaoRepository;
    @Mock FundoConsumoRepository fundoConsumoRepository;
    @Mock PagamentoGatewayRepository pagamentoGatewayRepository;
    @Mock SessaoOwnerActionTokenService ownerTokenService;

    SessaoConsumoService sessaoConsumoService;

    @BeforeEach
    void setUp() {
        sessaoConsumoService = new SessaoConsumoService(
                sessaoConsumoRepository,
                mesaRepository,
                clienteService,
                atendenteRepository,
                unidadeAtendimentoRepository,
                fundoConsumoService,
                pedidoFinanceiroService,
                pedidoRepository,
                qrCodeService,
                notificacaoService,
                instituicaoRepository,
                eventoSessaoRepository,
                fundoConsumoRepository,
                pagamentoGatewayRepository,
                ownerTokenService
        );
    }

    @Test
    @DisplayName("fechar sessão de consumo invoca a revogação de tokens e encerra a sessão")
    void fechar_sessao_invoca_revogacao() {
        Tenant tenant = new Tenant();
        tenant.setId(10L);

        SessaoConsumo sessao = new SessaoConsumo();
        sessao.setId(100L);
        sessao.setTenant(tenant);
        sessao.setStatus(StatusSessaoConsumo.ABERTA);

        when(sessaoConsumoRepository.findById(100L)).thenReturn(Optional.of(sessao));
        when(pedidoRepository.findBySessaoConsumoIdAndStatusInOrderByCreatedAtAsc(eq(100L), any()))
                .thenReturn(Collections.emptyList());
        when(sessaoConsumoRepository.save(any(SessaoConsumo.class))).thenAnswer(i -> i.getArgument(0));

        var response = sessaoConsumoService.fechar(100L);

        assertThat(response.getStatus()).isEqualTo(StatusSessaoConsumo.ENCERRADA);
        verify(ownerTokenService, times(1)).revokeActiveTokensBySessao(
                10L, 100L, "SESSION_CLOSE", null, null
        );
    }

    @Test
    @DisplayName("falha ao revogar tokens não impede o fechamento da sessão de consumo")
    void falha_revogacao_nao_impede_fechamento() {
        Tenant tenant = new Tenant();
        tenant.setId(10L);

        SessaoConsumo sessao = new SessaoConsumo();
        sessao.setId(100L);
        sessao.setTenant(tenant);
        sessao.setStatus(StatusSessaoConsumo.ABERTA);

        when(sessaoConsumoRepository.findById(100L)).thenReturn(Optional.of(sessao));
        when(pedidoRepository.findBySessaoConsumoIdAndStatusInOrderByCreatedAtAsc(eq(100L), any()))
                .thenReturn(Collections.emptyList());
        when(sessaoConsumoRepository.save(any(SessaoConsumo.class))).thenAnswer(i -> i.getArgument(0));

        // Força uma exceção na chamada de revogação
        doThrow(new RuntimeException("Database error on revocation"))
                .when(ownerTokenService)
                .revokeActiveTokensBySessao(anyLong(), anyLong(), anyString(), any(), any());

        var response = sessaoConsumoService.fechar(100L);

        assertThat(response.getStatus()).isEqualTo(StatusSessaoConsumo.ENCERRADA);
        verify(sessaoConsumoRepository, times(1)).save(sessao);
    }
}
