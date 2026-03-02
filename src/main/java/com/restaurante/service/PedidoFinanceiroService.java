package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.LimitePosPagoExcedidoException;
import com.restaurante.exception.PosPagoNaoPermitidoException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.exception.SaldoInsuficienteException;
import com.restaurante.model.entity.FundoConsumo;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Motor financeiro de pedidos.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Validar criação de pedido (PRE_PAGO vs POS_PAGO)</li>
 *   <li>Validar saldo suficiente no Fundo de Consumo (identificado ou anónimo)</li>
 *   <li>Processar pagamento de pedido</li>
 *   <li>Estornar pedido cancelado</li>
 *   <li>Registrar auditoria de todos os eventos críticos em banco</li>
 * </ul>
 *
 * <p>Regras de negócio:
 * <ul>
 *   <li>CLIENTE só pode criar PRE_PAGO (com saldo suficiente)</li>
 *   <li>POS_PAGO requer GERENTE ou ADMIN – anónimo NUNCA pode usar pós-pago</li>
 *   <li>PRE_PAGO debita automaticamente do Fundo (por clienteId ou por token)</li>
 *   <li>Cancelamento de pedido PAGO gera ESTORNO automático</li>
 *   <li>Toda ação crítica é registrada em {@link AuditoriaFinanceiraService}</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PedidoFinanceiroService {

    private final FundoConsumoService fundoConsumoService;
    private final PedidoRepository pedidoRepository;
    private final ConfiguracaoFinanceiraService configuracaoFinanceiraService;
    private final AuditoriaFinanceiraService auditoriaFinanceiraService;

    // Roles com permissão para autorizar pós-pago
    private static final Set<String> ROLES_AUTORIZAM_POS_PAGO = Set.of("GERENTE", "ADMIN");

    /**
     * Valida se pedido pode ser criado.
     *
     * @param clienteId    ID do cliente (nulo no fluxo anónimo)
     * @param fundoToken   Token QR Code do portador anónimo (nulo no fluxo identificado)
     * @param valorTotal   valor total do pedido
     * @param tipoPagamento PRE_PAGO ou POS_PAGO
     * @param roles        roles do usuário autenticado
     */
    public void validarCriacaoPedido(
            Long clienteId,
            String fundoToken,
            BigDecimal valorTotal,
            TipoPagamentoPedido tipoPagamento,
            Set<String> roles) {

        log.info("Validando criação de pedido: clienteId={}, fundoToken={}, valor={}, tipo={}, roles={}",
                clienteId, fundoToken != null ? "[TOKEN]" : "null", valorTotal, tipoPagamento, roles);

        if (tipoPagamento.isPosPago()) {
            // Consumo anónimo NUNCA pode usar pós-pago
            if (clienteId == null) {
                throw new BusinessException(
                        "Consumo anónimo não suporta pós-pago. Use o fundo de consumo pré-pago.");
            }
            autorizarPosPago(roles);
            configuracaoFinanceiraService.validarCriacaoPosPago(clienteId, valorTotal, roles);
        }

        if (tipoPagamento.isPrePago()) {
            if (clienteId != null) {
                validarSaldoSuficiente(clienteId, valorTotal);
            } else {
                // Fluxo anónimo: valida pelo token QR
                if (fundoToken == null || fundoToken.isBlank()) {
                    throw new BusinessException(
                            "Token do QR Code é obrigatório para pagamento pré-pago anónimo");
                }
                fundoConsumoService.validarSaldoSuficientePorToken(fundoToken, valorTotal);
            }
        }
    }

    /**
     * Valida se cliente identificado tem saldo suficiente no Fundo de Consumo.
     */
    public void validarSaldoSuficiente(Long clienteId, BigDecimal valorTotal) {
        log.info("Validando saldo suficiente para cliente {} - valor {}", clienteId, valorTotal);
        try {
            FundoConsumo fundo = fundoConsumoService.buscarPorCliente(clienteId);
            if (!fundo.temSaldoSuficiente(valorTotal)) {
                throw new SaldoInsuficienteException(fundo.getSaldoAtual(), valorTotal);
            }
        } catch (ResourceNotFoundException e) {
            throw new SaldoInsuficienteException(
                    "Fundo de Consumo não encontrado. Recarregue seu saldo antes de fazer pedidos.");
        }
    }

    /**
     * Valida se pedido pode ser confirmado automaticamente sem lançar exception.
     * Usado para confirmação automática de pedidos dentro do limite de risco.
     */
    @Transactional(readOnly = true)
    public boolean validarEConfirmarSePermitido(
            Long sessaoConsumoId,
            BigDecimal valorTotal,
            TipoPagamentoPedido tipoPagamento,
            Set<String> roles) {

        // PRE_PAGO sempre confirma automaticamente (saldo já validado)
        if (tipoPagamento.isPrePago()) {
            log.info("✅ PRE_PAGO: Confirmado automaticamente (saldo já validado)");
            return true;
        }

        // POS_PAGO: verifica limite sem lançar exception
        try {
            configuracaoFinanceiraService.validarCriacaoPosPago(sessaoConsumoId, valorTotal, roles);
            log.info("✅ POS_PAGO: DENTRO DO LIMITE - Confirmado automaticamente");
            return true;
        } catch (LimitePosPagoExcedidoException e) {
            log.warn("❌ POS_PAGO: LIMITE ATINGIDO - Pedido BLOQUEADO | {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifica se roles permitem autorizar pós-pago.
     */
    public void autorizarPosPago(Set<String> roles) {
        boolean temPermissao = roles.stream().anyMatch(ROLES_AUTORIZAM_POS_PAGO::contains);
        if (!temPermissao) {
            throw new PosPagoNaoPermitidoException();
        }
    }

    /**
     * Processa pagamento de pedido.
     * <ul>
     *   <li>PRE_PAGO identificado: debita Fundo de Consumo pelo clienteId</li>
     *   <li>PRE_PAGO anónimo: debita Fundo de Consumo pelo fundoToken</li>
     *   <li>POS_PAGO: apenas marca como NAO_PAGO (pagamento posterior)</li>
     * </ul>
     */
    @Transactional
    public void processarPagamentoPedido(
            Long pedidoId,
            Long clienteId,
            String fundoToken,
            BigDecimal valorTotal,
            TipoPagamentoPedido tipoPagamento) {

        log.info("Processando pagamento do pedido {}: tipo={}, valor={}", pedidoId, tipoPagamento, valorTotal);

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + pedidoId));

        // Idempotência
        if (pedido.isPago()) {
            log.info("Pedido {} já está pago. Ignorando processamento.", pedidoId);
            return;
        }

        if (tipoPagamento.isPrePago()) {
            if (clienteId != null) {
                fundoConsumoService.debitar(clienteId, pedidoId, valorTotal);
            } else {
                fundoConsumoService.debitarPorToken(fundoToken, pedidoId, valorTotal);
            }
            pedido.marcarComoPago();
        } else {
            // POS_PAGO: fica NAO_PAGO até confirmação manual
            log.info("Pedido {} criado como POS_PAGO. Aguardando confirmação de pagamento.", pedidoId);
        }

        pedidoRepository.save(pedido);
    }

    /**
     * Confirma pagamento de pedido POS_PAGO (GERENTE/ADMIN).
     * Registra auditoria em banco.
     */
    @Transactional
    public void confirmarPagamentoPosPago(Long pedidoId) {
        log.info("Confirmando pagamento pós-pago do pedido {}", pedidoId);

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + pedidoId));

        if (!pedido.getTipoPagamento().isPosPago()) {
            throw new BusinessException("Pedido não é pós-pago. Tipo: " + pedido.getTipoPagamento());
        }
        if (pedido.getStatusFinanceiro() != StatusFinanceiroPedido.NAO_PAGO) {
            throw new BusinessException("Pedido já possui status financeiro: " + pedido.getStatusFinanceiro());
        }

        pedido.marcarComoPago();
        pedidoRepository.save(pedido);

        // ✅ AUDITORIA em banco
        String usuarioNome = obterNomeUsuarioAutenticado();
        String usuarioRole = obterPrincipalRole();
        auditoriaFinanceiraService.registrarConfirmacaoPagamentoPosPago(
                pedidoId,
                pedido.getNumero(),
                pedido.getTotal(),
                usuarioNome,
                usuarioRole);

        log.info("Pagamento pós-pago confirmado para pedido {}", pedidoId);
    }

    /**
     * Estorna pedido cancelado.
     * <ul>
     *   <li>PRE_PAGO: devolve para Fundo de Consumo</li>
     *   <li>POS_PAGO: apenas marca como estornado</li>
     * </ul>
     * Registra auditoria em banco.
     *
     * @param motivo Motivo obrigatório do estorno
     */
    @Transactional
    public void estornarPedido(Long pedidoId, String motivo) {
        log.info("Estornando pedido {}: motivo={}", pedidoId, motivo);

        if (motivo == null || motivo.trim().isEmpty()) {
            throw new BusinessException("Motivo é obrigatório para estornar pedido");
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + pedidoId));

        // Idempotência
        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.ESTORNADO) {
            log.info("Pedido {} já foi estornado. Ignorando.", pedidoId);
            return;
        }

        if (!pedido.isPago()) {
            log.info("Pedido {} não estava pago. Não há o que estornar.", pedidoId);
            return;
        }

        if (pedido.getTipoPagamento().isPrePago()) {
            fundoConsumoService.estornar(pedidoId);
        }

        pedido.estornar();
        pedidoRepository.save(pedido);

        // ✅ AUDITORIA em banco
        String usuarioNome = obterNomeUsuarioAutenticado();
        String usuarioRole = obterPrincipalRole();
        auditoriaFinanceiraService.registrarEstornoPedido(
                pedidoId,
                pedido.getNumero(),
                pedido.getTotal(),
                usuarioNome,
                usuarioRole,
                motivo);

        log.info("Estorno concluído para pedido {} - Motivo: {}", pedidoId, motivo);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers de segurança
    // ──────────────────────────────────────────────────────────────────────────

    private String obterNomeUsuarioAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return "system";
        return auth.getName() != null ? auth.getName() : "system";
    }

    private String obterPrincipalRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return "SYSTEM";
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .findFirst()
                .orElse("SYSTEM");
    }
}
