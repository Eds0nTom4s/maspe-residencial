package com.restaurante.service;

import com.restaurante.config.SessaoExpiracaoScheduler;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do Sprint 1 — Blindagem da Expiração de Sessões.
 *
 * <p>Cobre os critérios de aceitação definidos no sprint:
 * - ultimaAtividadeEm é inicializado ao criar sessão
 * - Scheduler usa findCandidatasParaExpiracao (por ultimaAtividadeEm)
 * - expirarComSeguranca() valida todos os critérios de bloqueio
 * - Auditoria é gravada quando há expiração automática
 * - Uma exceção em uma sessão não para o lote do scheduler
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Sprint 1 — Expiração Segura de Sessões")
class SessaoExpiracaoSeguraTest {

    // ─── Dependências do SessaoConsumoService ───────────────────────────────

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

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private SessaoConsumo sessaoAberta;
    private FundoConsumo fundoAtivo;
    private LocalDateTime limiteInatividade;

    @BeforeEach
    void setUp() {
        limiteInatividade = LocalDateTime.now().minusHours(12);

        // Sessão aberta há 14 horas, última atividade há 14 horas (candidata)
        sessaoAberta = SessaoConsumo.builder()
                .status(StatusSessaoConsumo.ABERTA)
                .abertaEm(LocalDateTime.now().minusHours(14))
                .build();
        sessaoAberta.setId(1L);
        // Forçar ultimaAtividadeEm para 14h atrás (antiga)
        sessaoAberta.setUltimaAtividadeEm(LocalDateTime.now().minusHours(14));

        fundoAtivo = FundoConsumo.builder()
                .sessaoConsumo(sessaoAberta)
                .saldoAtual(BigDecimal.ZERO)
                .ativo(true)
                .build();
        fundoAtivo.setId(10L);
        sessaoAberta.setFundoConsumo(fundoAtivo);
    }

    // =========================================================================
    // 1. CAMPO ultimaAtividadeEm — inicialização
    // =========================================================================
    @Nested
    @DisplayName("1. Inicialização de ultimaAtividadeEm")
    class InicializacaoUltimaAtividadeEm {

        @Test
        @DisplayName("Sessão criada sem timestamp explícito deve ter ultimaAtividadeEm preenchido")
        void sessaoSemTimestampExplicitoDeveInicializarUltimaAtividade() {
            SessaoConsumo sessao = new SessaoConsumo();
            assertThat(sessao.getUltimaAtividadeEm())
                    .as("ultimaAtividadeEm deve ser inicializado por omissão")
                    .isNotNull();
        }

        @Test
        @DisplayName("Builder sem abertaEm deve inicializar ultimaAtividadeEm com valor próximo de agora")
        void builderDeveInicializarUltimaAtividadeEmComValorAtual() {
            LocalDateTime antes = LocalDateTime.now().minusSeconds(1);
            SessaoConsumo sessao = SessaoConsumo.builder().build();
            LocalDateTime depois = LocalDateTime.now().plusSeconds(1);

            assertThat(sessao.getUltimaAtividadeEm())
                    .isAfterOrEqualTo(antes)
                    .isBeforeOrEqualTo(depois);
        }

        @Test
        @DisplayName("Builder com abertaEm explícito deve copiar para ultimaAtividadeEm")
        void builderComAbertaEmDeveUsarMesmoValorEmUltimaAtividade() {
            LocalDateTime abertaEm = LocalDateTime.of(2025, 1, 1, 10, 0);
            SessaoConsumo sessao = SessaoConsumo.builder()
                    .abertaEm(abertaEm)
                    .build();

            assertThat(sessao.getUltimaAtividadeEm())
                    .isEqualTo(abertaEm);
        }

        @Test
        @DisplayName("registrarAtividade() em sessão ABERTA deve actualizar o timestamp")
        void registrarAtividadeDeveActualizarTimestamp() {
            LocalDateTime antes = sessaoAberta.getUltimaAtividadeEm();
            // Garantir que há diferença temporal
            try { Thread.sleep(5); } catch (InterruptedException ignored) {}

            sessaoAberta.registrarAtividade();

            assertThat(sessaoAberta.getUltimaAtividadeEm())
                    .isAfter(antes);
        }

        @Test
        @DisplayName("registrarAtividade() em sessão ENCERRADA não deve alterar o timestamp")
        void registrarAtividadeEmSessaoEncerradaNaoDeveActualizar() {
            sessaoAberta.encerrar();
            LocalDateTime ts = sessaoAberta.getUltimaAtividadeEm();
            try { Thread.sleep(5); } catch (InterruptedException ignored) {}

            sessaoAberta.registrarAtividade();

            assertThat(sessaoAberta.getUltimaAtividadeEm())
                    .isEqualTo(ts);
        }
    }

    // =========================================================================
    // 2. expirarComSeguranca — critérios de BLOQUEIO
    // =========================================================================
    @Nested
    @DisplayName("2. expirarComSeguranca() — critérios de bloqueio")
    class ExpirarComSegurancaBloqueios {

        @BeforeEach
        void setupMockBase() {
            when(sessaoConsumoRepository.findById(1L)).thenReturn(Optional.of(sessaoAberta));
        }

        @Test
        @DisplayName("Sessão não ABERTA deve ser ignorada (IGNORADA_STATUS_NAO_ABERTA)")
        void sessaoJaEncerradaDeveSerIgnorada() {
            sessaoAberta.setStatus(StatusSessaoConsumo.ENCERRADA);

            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.IGNORADA_STATUS_NAO_ABERTA);
            verify(sessaoConsumoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sessão com atividade recente deve ser bloqueada (BLOQUEADA_ATIVIDADE_RECENTE)")
        void sessaoComAtividadeRecenteDeveSerBloqueada() {
            // Actividade 1 hora atrás — dentro da janela de 12h
            sessaoAberta.setUltimaAtividadeEm(LocalDateTime.now().minusHours(1));

            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.BLOQUEADA_ATIVIDADE_RECENTE);
            verify(sessaoConsumoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sessão com saldo positivo deve ser bloqueada (BLOQUEADA_SALDO_POSITIVO)")
        void sessaoComSaldoPositivoDeveSerBloqueada() {
            fundoAtivo.atualizarSaldoCache(new BigDecimal("50000.00"));
            lenient().when(fundoConsumoRepository.findBySessaoConsumoId(1L))
                    .thenReturn(Optional.of(fundoAtivo));

            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.BLOQUEADA_SALDO_POSITIVO);
            verify(sessaoConsumoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sessão com pedido CRIADO deve ser bloqueada (BLOQUEADA_PEDIDO_PENDENTE)")
        void sessaoComPedidoCriadoDeveSerBloqueada() {
            lenient().when(fundoConsumoRepository.findBySessaoConsumoId(1L))
                    .thenReturn(Optional.of(fundoAtivo));
            lenient().when(pagamentoGatewayRepository.findByFundoConsumoIdOrderByCreatedAtDesc(10L))
                    .thenReturn(Collections.emptyList());

            Pedido pedidoCriado = Pedido.builder()
                    .numero("PED-001")
                    .status(StatusPedido.CRIADO)
                    .build();
            pedidoCriado.setId(100L);

            when(pedidoRepository.findBySessaoConsumoIdAndStatusInOrderByCreatedAtAsc(
                    eq(1L), anyList())).thenReturn(List.of(pedidoCriado));

            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.BLOQUEADA_PEDIDO_PENDENTE);
            verify(sessaoConsumoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sessão com pedido EM_ANDAMENTO deve ser bloqueada (BLOQUEADA_PEDIDO_PENDENTE)")
        void sessaoComPedidoEmAndamentoDeveSerBloqueada() {
            lenient().when(fundoConsumoRepository.findBySessaoConsumoId(1L))
                    .thenReturn(Optional.of(fundoAtivo));
            lenient().when(pagamentoGatewayRepository.findByFundoConsumoIdOrderByCreatedAtDesc(10L))
                    .thenReturn(Collections.emptyList());

            Pedido pedidoEmAndamento = Pedido.builder()
                    .numero("PED-002")
                    .status(StatusPedido.EM_ANDAMENTO)
                    .build();
            pedidoEmAndamento.setId(101L);

            when(pedidoRepository.findBySessaoConsumoIdAndStatusInOrderByCreatedAtAsc(
                    eq(1L), anyList())).thenReturn(List.of(pedidoEmAndamento));

            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.BLOQUEADA_PEDIDO_PENDENTE);
        }

        @Test
        @DisplayName("Sessão com pagamento PENDENTE deve ser bloqueada (BLOQUEADA_PAGAMENTO_PENDENTE)")
        void sessaoComPagamentoPendenteDeveSerBloqueada() {
            lenient().when(fundoConsumoRepository.findBySessaoConsumoId(1L))
                    .thenReturn(Optional.of(fundoAtivo));
            when(pedidoRepository.findBySessaoConsumoIdAndStatusInOrderByCreatedAtAsc(
                    eq(1L), anyList())).thenReturn(Collections.emptyList());

            Pagamento pagamentoPendente = new Pagamento();
            pagamentoPendente.setId(50L);
            pagamentoPendente.setStatus(StatusPagamentoGateway.PENDENTE);

            // Usa a nova query dedicada findPagamentosPendentesByFundoId (refatoração Sprint 1)
            when(pagamentoGatewayRepository.findPagamentosPendentesByFundoId(10L))
                    .thenReturn(List.of(pagamentoPendente));

            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.BLOQUEADA_PAGAMENTO_PENDENTE);
            verify(sessaoConsumoRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 3. expirarComSeguranca — EXPIRAÇÃO BEM-SUCEDIDA
    // =========================================================================
    @Nested
    @DisplayName("3. expirarComSeguranca() — expiração bem-sucedida")
    class ExpirarComSegurancaExpiracao {

        @BeforeEach
        void setupMocksLimpos() {
            lenient().when(sessaoConsumoRepository.findById(1L)).thenReturn(Optional.of(sessaoAberta));
            lenient().when(fundoConsumoRepository.findBySessaoConsumoId(1L)).thenReturn(Optional.of(fundoAtivo));
            lenient().when(pedidoRepository.findBySessaoConsumoIdAndStatusInOrderByCreatedAtAsc(
                    eq(1L), anyList())).thenReturn(Collections.emptyList());
            lenient().when(pagamentoGatewayRepository.findByFundoConsumoIdOrderByCreatedAtDesc(10L))
                    .thenReturn(Collections.emptyList());
            lenient().when(sessaoConsumoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(fundoConsumoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(eventoSessaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("Sessão elegível deve ser expirada (status=EXPIRADA, fechadaEm preenchido)")
        void sessaoElegívelDeveSerExpirada() {
            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.EXPIRADA);
            assertThat(sessaoAberta.getStatus()).isEqualTo(StatusSessaoConsumo.EXPIRADA);
            assertThat(sessaoAberta.getFechadaEm()).isNotNull();
        }

        @Test
        @DisplayName("Fundo com saldo zero deve ser encerrado junto com a sessão")
        void fundoComSaldoZeroDeveSerEncerrado() {
            sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            assertThat(fundoAtivo.getAtivo()).isFalse();
            verify(fundoConsumoRepository).save(fundoAtivo);
        }

        @Test
        @DisplayName("Auditoria deve ser gravada com tipo SESSAO_EXPIRADA_AUTOMATICAMENTE")
        void auditoriaDeveSerGravadaComTipoCorreto() {
            sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            ArgumentCaptor<EventoSessao> captor = ArgumentCaptor.forClass(EventoSessao.class);
            verify(eventoSessaoRepository).save(captor.capture());

            EventoSessao evento = captor.getValue();
            assertThat(evento.getTipoEvento())
                    .isEqualTo(TipoEventoSessao.SESSAO_EXPIRADA_AUTOMATICAMENTE);
            assertThat(evento.getUsuarioResponsavel()).isEqualTo("SCHEDULER");
            assertThat(evento.getDescricao()).contains("INATIVIDADE_SEGURA");
        }

        @Test
        @DisplayName("expirarComSeguranca é idempotente — segunda chamada retorna IGNORADA")
        void expiracaoDeveSerIdempotente() {
            // Primeira chamada expira
            sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);
            assertThat(sessaoAberta.getStatus()).isEqualTo(StatusSessaoConsumo.EXPIRADA);

            // Segunda chamada: sessão já não está ABERTA
            ExpiracaoSessaoResultado resultado2 =
                    sessaoConsumoService.expirarComSeguranca(1L, limiteInatividade);

            assertThat(resultado2).isEqualTo(ExpiracaoSessaoResultado.IGNORADA_STATUS_NAO_ABERTA);
        }

        @Test
        @DisplayName("Sessão não encontrada deve retornar IGNORADA")
        void sessaoNaoEncontradaDeveRetornarIgnorada() {
            when(sessaoConsumoRepository.findById(999L)).thenReturn(Optional.empty());

            ExpiracaoSessaoResultado resultado =
                    sessaoConsumoService.expirarComSeguranca(999L, limiteInatividade);

            assertThat(resultado).isEqualTo(ExpiracaoSessaoResultado.IGNORADA_STATUS_NAO_ABERTA);
        }
    }

    // =========================================================================
    // 4. SCHEDULER — comportamento do lote
    // =========================================================================
    @Nested
    @DisplayName("4. SessaoExpiracaoScheduler — comportamento do lote")
    class SchedulerComportamento {

        @Mock private SessaoConsumoService mockService;

        @Test
        @DisplayName("Exceção numa sessão não para o lote — outras sessões continuam a ser processadas")
        void erroNumaSessionNaoParaLote() {
            // Duas sessões candidatas
            SessaoConsumo s1 = SessaoConsumo.builder().build();
            s1.setId(1L);
            SessaoConsumo s2 = SessaoConsumo.builder().build();
            s2.setId(2L);

            when(sessaoConsumoRepository.findCandidatasParaExpiracao(any()))
                    .thenReturn(List.of(s1, s2));

            // ID 1 lança excepção, ID 2 expira normalmente
            when(mockService.expirarComSeguranca(eq(1L), any()))
                    .thenThrow(new RuntimeException("Erro simulado"));
            when(mockService.expirarComSeguranca(eq(2L), any()))
                    .thenReturn(ExpiracaoSessaoResultado.EXPIRADA);

            SessaoExpiracaoScheduler scheduler =
                    new SessaoExpiracaoScheduler(sessaoConsumoRepository, mockService);
            assertDoesNotThrow(scheduler::expirarSessoesAbandonadas);

            // Ambas as sessões foram tentadas
            verify(mockService).expirarComSeguranca(eq(1L), any());
            verify(mockService).expirarComSeguranca(eq(2L), any());
        }

        @Test
        @DisplayName("Scheduler não altera sessão directamente — delega tudo ao service")
        void schedulerNaoAlteraSessaoDiretamente() {
            SessaoConsumo candidata = SessaoConsumo.builder().build();
            candidata.setId(5L);

            when(sessaoConsumoRepository.findCandidatasParaExpiracao(any()))
                    .thenReturn(List.of(candidata));
            when(mockService.expirarComSeguranca(eq(5L), any()))
                    .thenReturn(ExpiracaoSessaoResultado.EXPIRADA);

            SessaoExpiracaoScheduler scheduler =
                    new SessaoExpiracaoScheduler(sessaoConsumoRepository, mockService);
            scheduler.expirarSessoesAbandonadas();

            // O scheduler nunca deve salvar a sessão directamente
            verify(sessaoConsumoRepository, never()).save(any());
            // Mas deve chamar o service
            verify(mockService).expirarComSeguranca(eq(5L), any());
        }

        @Test
        @DisplayName("Sem candidatas — scheduler não chama o service")
        void semCandidatasSchedulerNaoChamaService() {
            when(sessaoConsumoRepository.findCandidatasParaExpiracao(any()))
                    .thenReturn(Collections.emptyList());

            SessaoExpiracaoScheduler scheduler =
                    new SessaoExpiracaoScheduler(sessaoConsumoRepository, mockService);
            scheduler.expirarSessoesAbandonadas();

            verify(mockService, never()).expirarComSeguranca(any(), any());
        }
    }

    // =========================================================================
    // 5. registrarAtividade() — cobertura de casos extremos
    // =========================================================================
    @Nested
    @DisplayName("5. registrarAtividade() via service")
    class RegistrarAtividadeViaService {

        @Test
        @DisplayName("registrarAtividade com ID nulo não deve lançar excepção")
        void registrarAtividadeComIdNuloNaoDeveLancarExcecao() {
            assertDoesNotThrow(() -> sessaoConsumoService.registrarAtividade((Long) null, "teste"));
        }

        @Test
        @DisplayName("registrarAtividade deve salvar a sessão com timestamp actualizado")
        void registrarAtividadeDeveSalvarSessao() {
            when(sessaoConsumoRepository.findById(1L)).thenReturn(Optional.of(sessaoAberta));
            when(sessaoConsumoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            sessaoConsumoService.registrarAtividade(1L, "Pedido criado");

            verify(sessaoConsumoRepository).save(sessaoAberta);
        }
    }
}
