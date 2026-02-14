package com.restaurante.service;

import com.restaurante.exception.PermissaoNegadaException;
import com.restaurante.exception.TransicaoInvalidaException;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.service.validator.TransicaoEstadoValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes da máquina de estados de SubPedido
 *
 * Foca em:
 * - Transições válidas e inválidas
 * - Permissões por role
 * - Idempotência
 * - Registro de EventLog e recalculo de status do Pedido
 */
@ExtendWith(MockitoExtension.class)
class SubPedidoStateMachineTest {

    @Mock
    private SubPedidoRepository subPedidoRepository;

    @Mock
    private CozinhaRepository cozinhaRepository;

    @Mock
    private UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    @Mock
    private EventLogService eventLogService;

    // Usamos implementação real para validar regras de transição e permissão
    private TransicaoEstadoValidator transicaoEstadoValidator;

    @Mock
    private PedidoService pedidoService;

    @Mock
    private Environment environment;

    private SubPedidoService subPedidoService;

    private Cozinha cozinha;
    private UnidadeAtendimento unidadeAtendimento;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        transicaoEstadoValidator = new TransicaoEstadoValidator(environment);
        subPedidoService = new SubPedidoService(
                subPedidoRepository,
                cozinhaRepository,
                unidadeAtendimentoRepository,
                eventLogService,
                transicaoEstadoValidator,
                pedidoService
        );

        cozinha = Cozinha.builder()
                .nome("Cozinha Central")
                .tipo(TipoCozinha.CENTRAL)
                .build();
        cozinha.setId(1L);

        unidadeAtendimento = UnidadeAtendimento.builder()
                .nome("Restaurante")
                .tipo(TipoUnidadeAtendimento.RESTAURANTE)
                .build();
        unidadeAtendimento.setId(1L);

        pedido = Pedido.builder()
                .numero("PED-TEST-001")
                .status(StatusPedido.CRIADO)
                .build();
        pedido.setId(10L);

        // Limpa SecurityContext entre os testes
        SecurityContextHolder.clearContext();
    }

    private SubPedido criarSubPedidoComStatus(StatusSubPedido status) {
        SubPedido subPedido = SubPedido.builder()
                .pedido(pedido)
                .cozinha(cozinha)
                .unidadeAtendimento(unidadeAtendimento)
                .status(status)
                .build();
        subPedido.setId(100L);
        return subPedido;
    }

    private void mockBuscarESalvar(SubPedido subPedidoAtual, SubPedido subPedidoSalvo) {
        when(subPedidoRepository.findById(subPedidoAtual.getId())).thenReturn(java.util.Optional.of(subPedidoAtual));
        when(subPedidoRepository.save(any(SubPedido.class))).thenReturn(subPedidoSalvo);
    }

    private void autenticarComo(String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "user",
                "password",
                List.of(new SimpleGrantedAuthority(role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ================== Transições válidas ==================

    @Test
    void deveTransicionarCriadoParaPendenteAoConfirmar() {
        autenticarComo("ROLE_ATENDENTE");
        SubPedido atual = criarSubPedidoComStatus(StatusSubPedido.CRIADO);
        SubPedido salvo = criarSubPedidoComStatus(StatusSubPedido.PENDENTE);

        mockBuscarESalvar(atual, salvo);

        SubPedido resultado = subPedidoService.confirmar(atual.getId());

        assertEquals(StatusSubPedido.PENDENTE, resultado.getStatus());
        verify(eventLogService, times(1)).registrarEventoSubPedido(
                any(SubPedido.class), eq(StatusSubPedido.CRIADO), eq(StatusSubPedido.PENDENTE),
                any(), any(), anyLong()
        );
        verify(pedidoService, times(1)).recalcularStatusPedido(pedido.getId());
    }

    @Test
    void deveTransicionarPendenteParaEmPreparacaoPorCozinha() {
        autenticarComo("ROLE_COZINHA");
        SubPedido atual = criarSubPedidoComStatus(StatusSubPedido.PENDENTE);
        SubPedido salvo = criarSubPedidoComStatus(StatusSubPedido.EM_PREPARACAO);

        mockBuscarESalvar(atual, salvo);

        SubPedido resultado = subPedidoService.assumir(atual.getId());

        assertEquals(StatusSubPedido.EM_PREPARACAO, resultado.getStatus());
        verify(eventLogService, times(1)).registrarEventoSubPedido(
                any(SubPedido.class), eq(StatusSubPedido.PENDENTE), eq(StatusSubPedido.EM_PREPARACAO),
                any(), any(), anyLong()
        );
        verify(pedidoService, times(1)).recalcularStatusPedido(pedido.getId());
    }

    @Test
    void deveTransicionarEmPreparacaoParaProntoPorCozinha() {
        autenticarComo("ROLE_COZINHA");
        SubPedido atual = criarSubPedidoComStatus(StatusSubPedido.EM_PREPARACAO);
        SubPedido salvo = criarSubPedidoComStatus(StatusSubPedido.PRONTO);

        mockBuscarESalvar(atual, salvo);

        SubPedido resultado = subPedidoService.marcarPronto(atual.getId());

        assertEquals(StatusSubPedido.PRONTO, resultado.getStatus());
        verify(eventLogService, times(1)).registrarEventoSubPedido(
                any(SubPedido.class), eq(StatusSubPedido.EM_PREPARACAO), eq(StatusSubPedido.PRONTO),
                any(), any(), anyLong()
        );
        verify(pedidoService, times(1)).recalcularStatusPedido(pedido.getId());
    }

    @Test
    void deveTransicionarProntoParaEntreguePorAtendente() {
        autenticarComo("ROLE_ATENDENTE");
        SubPedido atual = criarSubPedidoComStatus(StatusSubPedido.PRONTO);
        SubPedido salvo = criarSubPedidoComStatus(StatusSubPedido.ENTREGUE);

        mockBuscarESalvar(atual, salvo);

        SubPedido resultado = subPedidoService.marcarEntregue(atual.getId());

        assertEquals(StatusSubPedido.ENTREGUE, resultado.getStatus());
        verify(eventLogService, times(1)).registrarEventoSubPedido(
                any(SubPedido.class), eq(StatusSubPedido.PRONTO), eq(StatusSubPedido.ENTREGUE),
                any(), any(), anyLong()
        );
        verify(pedidoService, times(1)).recalcularStatusPedido(pedido.getId());
    }

    // ================== Transições inválidas ==================

    @Test
    void naoDevePermitirProntoParaEmPreparacao() {
        autenticarComo("ROLE_COZINHA");
        SubPedido atual = criarSubPedidoComStatus(StatusSubPedido.PRONTO);
        when(subPedidoRepository.findById(atual.getId())).thenReturn(java.util.Optional.of(atual));

        assertThrows(TransicaoInvalidaException.class,
                () -> subPedidoService.alterarStatus(atual.getId(), StatusSubPedido.EM_PREPARACAO, "retornar"));

        verify(eventLogService, never()).registrarEventoSubPedido(any(), any(), any(), any(), any(), anyLong());
        verify(pedidoService, never()).recalcularStatusPedido(anyLong());
    }

    @Test
    void naoDevePermitirAlterarEstadoQuandoEntregue() {
        autenticarComo("ROLE_ATENDENTE");
        SubPedido atual = criarSubPedidoComStatus(StatusSubPedido.ENTREGUE);
        when(subPedidoRepository.findById(atual.getId())).thenReturn(java.util.Optional.of(atual));

        assertThrows(TransicaoInvalidaException.class,
                () -> subPedidoService.alterarStatus(atual.getId(), StatusSubPedido.PRONTO, "invalido"));
    }

    @Test
    void naoDevePermitirAlterarEstadoQuandoCancelado() {
        autenticarComo("ROLE_GERENTE");
        SubPedido atual = criarSubPedidoComStatus(StatusSubPedido.CANCELADO);
        when(subPedidoRepository.findById(atual.getId())).thenReturn(java.util.Optional.of(atual));

        assertThrows(TransicaoInvalidaException.class,
                () -> subPedidoService.alterarStatus(atual.getId(), StatusSubPedido.PENDENTE, "invalido"));
    }

    // ================== Permissões ==================

    @Test
    void clienteNaoPodeAlterarEstado() {
        autenticarComo("ROLE_CLIENTE");
        SubPedido atual = criarSubPedidoComStatus(StatusSubPedido.PENDENTE);
        when(subPedidoRepository.findById(atual.getId())).thenReturn(java.util.Optional.of(atual));

        assertThrows(PermissaoNegadaException.class,
                () -> subPedidoService.assumir(atual.getId()));
    }

    @Test
    void cozinhaNaoPodeMarcarEntregue() {
        autenticarComo("ROLE_COZINHA");
        SubPedido atual = criarSubPedidoComStatus(StatusSubPedido.PRONTO);
        when(subPedidoRepository.findById(atual.getId())).thenReturn(java.util.Optional.of(atual));

        assertThrows(PermissaoNegadaException.class,
                () -> subPedidoService.marcarEntregue(atual.getId()));
    }

    @Test
    void atendenteNaoPodeAssumirPreparacao() {
        autenticarComo("ROLE_ATENDENTE");
        SubPedido atual = criarSubPedidoComStatus(StatusSubPedido.PENDENTE);
        when(subPedidoRepository.findById(atual.getId())).thenReturn(java.util.Optional.of(atual));

        assertThrows(PermissaoNegadaException.class,
                () -> subPedidoService.assumir(atual.getId()));
    }

    @Test
    void gerentePodeCancelarEmEstadoNaoTerminal() {
        autenticarComo("ROLE_GERENTE");
        SubPedido atual = criarSubPedidoComStatus(StatusSubPedido.EM_PREPARACAO);
        SubPedido salvo = criarSubPedidoComStatus(StatusSubPedido.CANCELADO);

        mockBuscarESalvar(atual, salvo);

        SubPedido resultado = subPedidoService.cancelar(atual.getId(), "motivo gerencial");

        assertEquals(StatusSubPedido.CANCELADO, resultado.getStatus());
        verify(eventLogService, times(1)).registrarEventoSubPedido(
                any(SubPedido.class), eq(StatusSubPedido.EM_PREPARACAO), eq(StatusSubPedido.CANCELADO),
                any(), any(), anyLong()
        );
        verify(pedidoService, times(1)).recalcularStatusPedido(pedido.getId());
    }

    // ================== Idempotência ==================

    @Test
    void marcarProntoQuandoJaEstaProntoDeveSerNoOp() {
        autenticarComo("ROLE_COZINHA");
        SubPedido atual = criarSubPedidoComStatus(StatusSubPedido.PRONTO);
        when(subPedidoRepository.findById(atual.getId())).thenReturn(java.util.Optional.of(atual));

        SubPedido resultado = subPedidoService.marcarPronto(atual.getId());

        assertSame(atual, resultado);
        assertEquals(StatusSubPedido.PRONTO, resultado.getStatus());

        verify(subPedidoRepository, never()).save(any(SubPedido.class));
        verify(eventLogService, never()).registrarEventoSubPedido(any(), any(), any(), any(), any(), anyLong());
        verify(pedidoService, never()).recalcularStatusPedido(anyLong());
    }

    @Test
    void marcarEntregueQuandoJaEstaEntregueDeveSerNoOp() {
        autenticarComo("ROLE_ATENDENTE");
        SubPedido atual = criarSubPedidoComStatus(StatusSubPedido.ENTREGUE);
        when(subPedidoRepository.findById(atual.getId())).thenReturn(java.util.Optional.of(atual));

        SubPedido resultado = subPedidoService.marcarEntregue(atual.getId());

        assertSame(atual, resultado);
        assertEquals(StatusSubPedido.ENTREGUE, resultado.getStatus());

        verify(subPedidoRepository, never()).save(any(SubPedido.class));
        verify(eventLogService, never()).registrarEventoSubPedido(any(), any(), any(), any(), any(), anyLong());
        verify(pedidoService, never()).recalcularStatusPedido(anyLong());
    }

}
