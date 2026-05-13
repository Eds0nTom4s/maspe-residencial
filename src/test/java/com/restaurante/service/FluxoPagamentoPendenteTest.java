package com.restaurante.service;

import com.restaurante.config.SessaoExpiracaoScheduler;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes do fluxo de pagamento PENDENTE (referência bancária AppyPay).
 *
 * <p>Cenários cobertos:
 * <ol>
 *   <li>Sessão antiga com pagamento PENDENTE existente → scheduler bloqueado</li>
 *   <li>Pagamento PENDENTE confirmado via callback → ultimaAtividadeEm actualizada
 *       → scheduler bloqueado (actividade recente OU saldo positivo)</li>
 *   <li>Criação de pagamento PENDENTE regista actividade na sessão</li>
 *   <li>Expiração só ocorre quando não há pagamentos PENDENTE</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Fluxo Pagamento PENDENTE — Blindagem de Sessão")
class FluxoPagamentoPendenteTest {

    // ── Mocks do SessaoConsumoService ─────────────────────────────────────────

    @Mock private SessaoConsumoRepository sessaoConsumoRepository;
    @Mock private MesaRepository mesaRepository;
    @Mock private ClienteService clienteService;
    @Mock private AtendenteRepository atendenteRepository;
    @Mock private UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Mock private FundoConsumoService fundoConsumoService;
    @Mock private PedidoFinanceiroService pedidoFinanceiroService;
    @Mock private PedidoRepository pedidoRepository;
    @Mock private QrCodeService qrCodeService;
    @Mock private com.restaurante.notificacao.service.NotificacaoService notificacaoService;
    @Mock private InstituicaoRepository instituicaoRepository;
    @Mock private EventoSessaoRepository eventoSessaoRepository;
    @Mock private FundoConsumoRepository fundoConsumoRepository;
    @Mock private PagamentoGatewayRepository pagamentoGatewayRepository;

    @InjectMocks
    private SessaoConsumoService sessaoConsumoService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private SessaoConsumo sessaoAntiga;
    private FundoConsumo fundo;
    private LocalDateTime limiteInatividade;

    /** Pagamento REF criado e aguardando pagamento no Multicaixa (típico cenário de corrida). */
    private Pagamento pagamentoPendenteRef;

    @BeforeEach
    void setUp() {
        // Sessão aberta há 14h, última actividade há 14h (candidata à expiração)
        limiteInatividade = LocalDateTime.now().minusHours(12);

        sessaoAntiga = SessaoConsumo.builder()
                .status(StatusSessaoConsumo.ABERTA)
                .abertaEm(LocalDateTime.now().minusHours(14))
                .build();
        sessaoAntiga.setId(1L);
        sessaoAntiga.setUltimaAtividadeEm(LocalDateTime.now().minusHours(14));

        fundo = FundoConsumo.builder()
                .sessaoConsumo(sessaoAntiga)
                .saldoAtual(BigDecimal.ZERO)
                .ativo(true)
                .build();
        fundo.setId(10L);
        sessaoAntiga.setFundoConsumo(fundo);

        // Referência bancária criada e ainda PENDENTE (cliente ainda não pagou)
        pagamentoPendenteRef = new Pagamento();
        pagamentoPendenteRef.setId(99L);
        pagamentoPendenteRef.setStatus(StatusPagamentoGateway.PENDENTE);
        pagamentoPendenteRef.setFundoConsumo(fundo);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 1. Sessão antiga + pagamento PENDENTE → scheduler bloqueado
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("1. Scheduler bloqueado por pagamento PENDENTE")
    class SchedulerBloqueadoPorPagamentoPendente {

        @Test
        @DisplayName("Sessão com pagamento PENDENTE não deve ser expirada pelo scheduler")
        void sessaoComPagamentoPendenteNaoDeveSerExpirada() {
            // Arrange
            when(sessaoConsumoRepository.findById(1L)).thenReturn(Optional.of(sessaoAntiga));
            lenient().when(fundoConsumoRepository.findBySessaoConsumoId(1L))
                    .thenReturn(Optional.of(fundo));
            lenient().when(pedidoRepository.findBySessaoConsumoIdAndStatusInOrderByCreatedAtAsc(
                    eq(1L), anyList())).thenReturn(Collections.emptyList());
            // Retorna 1 pagamento PENDENTE via nova query dedicada
            when(pagamentoGatewayRepository.findPagamentosPendentesByFundoId(10L))
                    .thenReturn(List.of(pagamentoPendenteRef));

            // Act
            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            // Assert — deve bloquear, nunca alterar sessão
            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.BLOQUEADA_PAGAMENTO_PENDENTE);
            verify(sessaoConsumoRepository, never()).save(any());
            verify(eventoSessaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Status da sessão permanece ABERTA quando bloqueada por pagamento PENDENTE")
        void statusSessaoPermaneceAbertaQuandoBloqueada() {
            when(sessaoConsumoRepository.findById(1L)).thenReturn(Optional.of(sessaoAntiga));
            lenient().when(fundoConsumoRepository.findBySessaoConsumoId(1L))
                    .thenReturn(Optional.of(fundo));
            lenient().when(pedidoRepository.findBySessaoConsumoIdAndStatusInOrderByCreatedAtAsc(
                    eq(1L), anyList())).thenReturn(Collections.emptyList());
            when(pagamentoGatewayRepository.findPagamentosPendentesByFundoId(10L))
                    .thenReturn(List.of(pagamentoPendenteRef));

            sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            assertThat(sessaoAntiga.getStatus()).isEqualTo(StatusSessaoConsumo.ABERTA);
            assertThat(fundo.getAtivo()).isTrue();
        }

        @Test
        @DisplayName("Fundo permanece ativo quando sessão bloqueada por pagamento PENDENTE")
        void fundoPermaneceAtivoQuandoSessaoBloqueada() {
            when(sessaoConsumoRepository.findById(1L)).thenReturn(Optional.of(sessaoAntiga));
            lenient().when(fundoConsumoRepository.findBySessaoConsumoId(1L))
                    .thenReturn(Optional.of(fundo));
            lenient().when(pedidoRepository.findBySessaoConsumoIdAndStatusInOrderByCreatedAtAsc(
                    eq(1L), anyList())).thenReturn(Collections.emptyList());
            when(pagamentoGatewayRepository.findPagamentosPendentesByFundoId(10L))
                    .thenReturn(List.of(pagamentoPendenteRef));

            sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            verify(fundoConsumoRepository, never()).save(any());
            assertThat(fundo.getAtivo()).isTrue();
        }

        @Test
        @DisplayName("Scheduler com sessão antiga + pagamento PENDENTE: lote não expira nada")
        void schedulerNaoExpiraLoteComPagamentoPendente() {
            // Setup: scheduler encontra a sessão candidata
            when(sessaoConsumoRepository.findCandidatasParaExpiracao(any()))
                    .thenReturn(List.of(sessaoAntiga));

            // O service delega e recebe BLOQUEADA_PAGAMENTO_PENDENTE
            SessaoConsumoService mockService = mock(SessaoConsumoService.class);
            when(mockService.expirarComSeguranca(eq(1L), any()))
                    .thenReturn(ExpiracaoSessaoResultado.BLOQUEADA_PAGAMENTO_PENDENTE);

            SessaoExpiracaoScheduler scheduler =
                    new SessaoExpiracaoScheduler(sessaoConsumoRepository, mockService);

            // Act + Assert — scheduler não lança excepção; sessão não foi alterada
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    scheduler::expirarSessoesAbandonadas);
            verify(sessaoConsumoRepository, never()).save(any());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2. Pagamento PENDENTE → CONFIRMADO via callback → sessão protegida
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("2. Pagamento CONFIRMADO via callback — protecção pós-webhook")
    class PagamentoConfirmadoViaCallback {

        @Test
        @DisplayName("Após confirmação, sessão com actividade recente bloqueia expiração")
        void aposConfirmacaoAtividadeRecenteBloqueia() {
            // Simula: callback chegou e registou actividade há 30 segundos
            sessaoAntiga.setUltimaAtividadeEm(LocalDateTime.now().minusSeconds(30));

            when(sessaoConsumoRepository.findById(1L)).thenReturn(Optional.of(sessaoAntiga));

            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.BLOQUEADA_ATIVIDADE_RECENTE);
            assertThat(sessaoAntiga.getStatus()).isEqualTo(StatusSessaoConsumo.ABERTA);
        }

        @Test
        @DisplayName("Após confirmação, sessão com saldo positivo bloqueia expiração")
        void aposConfirmacaoSaldoPositivoBloqueia() {
            // Simulação: callback confirmou pagamento e creditou saldo
            fundo.atualizarSaldoCache(new BigDecimal("15000.00"));

            when(sessaoConsumoRepository.findById(1L)).thenReturn(Optional.of(sessaoAntiga));
            lenient().when(fundoConsumoRepository.findBySessaoConsumoId(1L))
                    .thenReturn(Optional.of(fundo));

            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.BLOQUEADA_SALDO_POSITIVO);
            assertThat(sessaoAntiga.getStatus()).isEqualTo(StatusSessaoConsumo.ABERTA);
            assertThat(fundo.getAtivo()).isTrue();
        }

        @Test
        @DisplayName("Ambas as protecções activas simultaneamente (actividade recente + saldo positivo)")
        void ambasProteccoesAtivas() {
            // Estado: callback recebido há 2 min E saldo creditado
            sessaoAntiga.setUltimaAtividadeEm(LocalDateTime.now().minusMinutes(2));
            fundo.atualizarSaldoCache(new BigDecimal("8000.00"));

            when(sessaoConsumoRepository.findById(1L)).thenReturn(Optional.of(sessaoAntiga));
            lenient().when(fundoConsumoRepository.findBySessaoConsumoId(1L))
                    .thenReturn(Optional.of(fundo));

            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            // A actividade recente deve ser verificada antes do saldo (short-circuit)
            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.BLOQUEADA_ATIVIDADE_RECENTE);
            assertThat(sessaoAntiga.getStatus()).isEqualTo(StatusSessaoConsumo.ABERTA);
        }

        @Test
        @DisplayName("Pagamento PENDENTE vira CONFIRMADO → lista PENDENTE fica vazia → expiração permitida (se não houver outro bloqueio)")
        void apósPagamentoConfirmadoListaPendenteVaziaPermiteExpiracao() {
            // Sessão antiga, sem actividade recente, saldo zero após débito total, sem pedidos
            when(sessaoConsumoRepository.findById(1L)).thenReturn(Optional.of(sessaoAntiga));
            lenient().when(fundoConsumoRepository.findBySessaoConsumoId(1L))
                    .thenReturn(Optional.of(fundo));
            lenient().when(pedidoRepository.findBySessaoConsumoIdAndStatusInOrderByCreatedAtAsc(
                    eq(1L), anyList())).thenReturn(Collections.emptyList());
            // Pagamento já foi confirmado → lista PENDENTE está vazia
            when(pagamentoGatewayRepository.findPagamentosPendentesByFundoId(10L))
                    .thenReturn(Collections.emptyList());
            lenient().when(sessaoConsumoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(fundoConsumoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(eventoSessaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            // Agora pode expirar legitimamente
            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.EXPIRADA);
            assertThat(sessaoAntiga.getStatus()).isEqualTo(StatusSessaoConsumo.EXPIRADA);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3. registrarAtividade ao criar pagamento PENDENTE
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("3. registrarAtividade() ao criar pagamento PENDENTE")
    class RegistrarAtividadeAoCriarPendente {

        @Test
        @DisplayName("Criar pagamento PENDENTE deve actualizar ultimaAtividadeEm via registrarAtividade()")
        void criarPagamentoPendenteActualizaUltimaAtividade() {
            // Simula chamada directa a registrarAtividade com ID
            when(sessaoConsumoRepository.findById(1L)).thenReturn(Optional.of(sessaoAntiga));
            when(sessaoConsumoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LocalDateTime antes = sessaoAntiga.getUltimaAtividadeEm();
            try { Thread.sleep(5); } catch (InterruptedException ignored) {}

            // Simula o que PagamentoGatewayService.criarPagamentoRecargaFundo() faz
            sessaoConsumoService.registrarAtividade(1L, "Pagamento PENDENTE criado: 5000 Kz (ref=REF-001)");

            assertThat(sessaoAntiga.getUltimaAtividadeEm())
                    .as("ultimaAtividadeEm deve ser actualizado após criação de pagamento PENDENTE")
                    .isAfter(antes);
            verify(sessaoConsumoRepository).save(sessaoAntiga);
        }

        @Test
        @DisplayName("Após registrar actividade do PENDENTE, sessão fica imune à expiração automática")
        void aposRegistrarAtividadePendenteSessionFicaImune() {
            // Simula: pagamento PENDENTE acabou de ser criado → actividade registada há 1 seg
            sessaoAntiga.setUltimaAtividadeEm(LocalDateTime.now().minusSeconds(1));

            when(sessaoConsumoRepository.findById(1L)).thenReturn(Optional.of(sessaoAntiga));

            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.BLOQUEADA_ATIVIDADE_RECENTE);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 4. Transição completa: PENDENTE → sem bloqueio → EXPIRADA
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("4. Ciclo de vida completo do pagamento")
    class CicloDeVidaCompleto {

        @Test
        @DisplayName("Ciclo PENDENTE → CONFIRMADO → saldo debitado → sem bloqueios → EXPIRADA")
        void cicloCompletoPendenteConfirmadoExpirada() {
            // Estado final: pagamento confirmado, saldo debitado (zero), sem pedidos,
            // lista PENDENTE vazia, sem actividade recente — sessão deve expirar
            when(sessaoConsumoRepository.findById(1L)).thenReturn(Optional.of(sessaoAntiga));
            lenient().when(fundoConsumoRepository.findBySessaoConsumoId(1L))
                    .thenReturn(Optional.of(fundo));
            lenient().when(pedidoRepository.findBySessaoConsumoIdAndStatusInOrderByCreatedAtAsc(
                    eq(1L), anyList())).thenReturn(Collections.emptyList());
            when(pagamentoGatewayRepository.findPagamentosPendentesByFundoId(10L))
                    .thenReturn(Collections.emptyList());
            lenient().when(sessaoConsumoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(fundoConsumoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(eventoSessaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.EXPIRADA);
            assertThat(sessaoAntiga.getStatus()).isEqualTo(StatusSessaoConsumo.EXPIRADA);
            assertThat(sessaoAntiga.getFechadaEm()).isNotNull();
        }

        @Test
        @DisplayName("Múltiplos pagamentos PENDENTE: qualquer um bloqueia a expiração")
        void multiplosPagamentosPendenteBloqueiam() {
            Pagamento p1 = new Pagamento();
            p1.setId(100L); p1.setStatus(StatusPagamentoGateway.PENDENTE);

            Pagamento p2 = new Pagamento();
            p2.setId(101L); p2.setStatus(StatusPagamentoGateway.PENDENTE);

            when(sessaoConsumoRepository.findById(1L)).thenReturn(Optional.of(sessaoAntiga));
            lenient().when(fundoConsumoRepository.findBySessaoConsumoId(1L))
                    .thenReturn(Optional.of(fundo));
            lenient().when(pedidoRepository.findBySessaoConsumoIdAndStatusInOrderByCreatedAtAsc(
                    eq(1L), anyList())).thenReturn(Collections.emptyList());
            when(pagamentoGatewayRepository.findPagamentosPendentesByFundoId(10L))
                    .thenReturn(List.of(p1, p2));

            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.BLOQUEADA_PAGAMENTO_PENDENTE);
            assertThat(sessaoAntiga.getStatus()).isEqualTo(StatusSessaoConsumo.ABERTA);
        }
    }
}
