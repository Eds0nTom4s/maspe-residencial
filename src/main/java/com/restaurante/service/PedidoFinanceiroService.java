package com.restaurante.service;

import com.restaurante.exception.BusinessException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Service validador financeiro
 * 
 * RESPONSABILIDADES:
 * - Validar criação de pedido (PRE_PAGO vs POS_PAGO)
 * - Validar saldo suficiente no Fundo de Consumo
 * - Processar pagamento de pedido
 * - Estornar pedido cancelado
 * 
 * REGRAS DE NEGÓCIO:
 * - CLIENTE só pode criar PRE_PAGO (com saldo suficiente)
 * - POS_PAGO requer GERENTE ou ADMIN
 * - PRE_PAGO debita automaticamente do Fundo
 * - Cancelamento de pedido PAGO gera ESTORNO automático
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PedidoFinanceiroService {

    private final FundoConsumoService fundoConsumoService;
    private final PedidoRepository pedidoRepository;
    private final ConfiguracaoFinanceiraService configuracaoFinanceiraService;

    // Roles com permissão para autorizar pós-pago
    private static final Set<String> ROLES_AUTORIZAM_POS_PAGO = Set.of("GERENTE", "ADMIN");

    /**
     * Valida se pedido pode ser criado
     * 
     * @param clienteId cliente do pedido
     * @param valorTotal valor total do pedido
     * @param tipoPagamento PRE_PAGO ou POS_PAGO
     * @param roles roles do usuário autenticado
     * @throws PosPagoNaoPermitidoException se cliente tenta POS_PAGO
     * @throws PosPagoDesabilitadoException se POS_PAGO está desabilitado globalmente
     * @throws LimitePosPagoExcedidoException se limite de pós-pago excedido
     * @throws SaldoInsuficienteException se PRE_PAGO sem saldo
     */
    public void validarCriacaoPedido(Long clienteId, BigDecimal valorTotal, TipoPagamentoPedido tipoPagamento, Set<String> roles) {
        log.info("Validando criação de pedido: cliente={}, valor={}, tipo={}, roles={}", 
                 clienteId, valorTotal, tipoPagamento, roles);

        // Valida tipo de pagamento vs roles
        if (tipoPagamento.isPosPago()) {
            autorizarPosPago(roles);
            // VALIDAÇÃO CRÍTICA: Verifica interruptor global e limites
            configuracaoFinanceiraService.validarCriacaoPosPago(clienteId, valorTotal, roles);
        }

        // Se PRE_PAGO, valida saldo
        if (tipoPagamento.isPrePago()) {
            validarSaldoSuficiente(clienteId, valorTotal);
        }
    }

    /**
     * Valida se cliente tem saldo suficiente no Fundo de Consumo
     */
    public void validarSaldoSuficiente(Long clienteId, BigDecimal valorTotal) {
        log.info("Validando saldo suficiente para cliente {} - valor {}", clienteId, valorTotal);

        try {
            FundoConsumo fundo = fundoConsumoService.buscarPorCliente(clienteId);
            
            if (!fundo.temSaldoSuficiente(valorTotal)) {
                throw new SaldoInsuficienteException(fundo.getSaldoAtual(), valorTotal);
            }
        } catch (ResourceNotFoundException e) {
            // Cliente não tem fundo ativo
            throw new SaldoInsuficienteException("Fundo de Consumo não encontrado. Recarregue seu saldo antes de fazer pedidos.");
        }
    }

    /**
     * Verifica se roles permitem autorizar pós-pago
     * 
     * @throws PosPagoNaoPermitidoException se não autorizado
     */
    public void autorizarPosPago(Set<String> roles) {
        boolean temPermissao = roles.stream()
            .anyMatch(ROLES_AUTORIZAM_POS_PAGO::contains);

        if (!temPermissao) {
            throw new PosPagoNaoPermitidoException();
        }
    }

    /**
     * Processa pagamento de pedido
     * - PRE_PAGO: debita Fundo de Consumo
     * - POS_PAGO: apenas marca como pago (pagamento manual)
     */
    @Transactional
    public void processarPagamentoPedido(Long pedidoId, Long clienteId, BigDecimal valorTotal, TipoPagamentoPedido tipoPagamento) {
        log.info("Processando pagamento do pedido {}: tipo={}, valor={}", pedidoId, tipoPagamento, valorTotal);

        Pedido pedido = pedidoRepository.findById(pedidoId)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + pedidoId));

        // Se já está pago, retorna (idempotência)
        if (pedido.isPago()) {
            log.info("Pedido {} já está pago. Ignorando processamento.", pedidoId);
            return;
        }

        if (tipoPagamento.isPrePago()) {
            // PRE_PAGO: debita do Fundo de Consumo
            fundoConsumoService.debitar(clienteId, pedidoId, valorTotal);
            pedido.marcarComoPago();
        } else {
            // POS_PAGO: apenas marca como não pago (pagamento posterior)
            // Não faz nada aqui - pedido fica NAO_PAGO até confirmação manual
            log.info("Pedido {} criado como POS_PAGO. Aguardando confirmação de pagamento.", pedidoId);
        }

        pedidoRepository.save(pedido);
    }

    /**
     * Confirma pagamento de pedido POS_PAGO
     * (método para GERENTE/ADMIN confirmar pagamento manual)
     * 
     * AUDITORIA: Gera evento CONFIRMACAO_PAGAMENTO_POS_PAGO
     */
    @Transactional
    public void confirmarPagamentoPosPago(Long pedidoId) {
        log.info("Confirmando pagamento pós-pago do pedido {}", pedidoId);

        Pedido pedido = pedidoRepository.findById(pedidoId)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + pedidoId));

        // Valida que é POS_PAGO
        if (!pedido.getTipoPagamento().isPosPago()) {
            throw new BusinessException("Pedido não é pós-pago. Tipo: " + pedido.getTipoPagamento());
        }

        // Valida que está NAO_PAGO
        if (pedido.getStatusFinanceiro() != StatusFinanceiroPedido.NAO_PAGO) {
            throw new BusinessException("Pedido já possui status financeiro: " + pedido.getStatusFinanceiro());
        }

        // Marca como pago
        pedido.marcarComoPago();
        pedidoRepository.save(pedido);

        // TODO: Registrar evento de auditoria CONFIRMACAO_PAGAMENTO_POS_PAGO
        // eventLogService.registrarEventoFinanceiro(pedido, TipoEvento.CONFIRMACAO_PAGAMENTO_POS_PAGO, ...)

        log.info("Pagamento pós-pago confirmado para pedido {}", pedidoId);
    }

    /**
     * Estorna pedido cancelado
     * - Se PRE_PAGO: devolve para Fundo de Consumo
     * - Se POS_PAGO: apenas marca como estornado
     * 
     * @param motivo Motivo obrigatório do estorno
     * AUDITORIA: Gera evento ESTORNO_MANUAL
     */
    @Transactional
    public void estornarPedido(Long pedidoId, String motivo) {
        log.info("Estornando pedido {}: motivo={}", pedidoId, motivo);

        // Validação de motivo obrigatório
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new BusinessException("Motivo é obrigatório para estornar pedido");
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + pedidoId));

        // Se já estornado, retorna (idempotência)
        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.ESTORNADO) {
            log.info("Pedido {} já foi estornado. Ignorando.", pedidoId);
            return;
        }

        // Só estorna se estava PAGO
        if (!pedido.isPago()) {
            log.info("Pedido {} não estava pago. Não há o que estornar.", pedidoId);
            return;
        }

        // Se PRE_PAGO, devolve para Fundo de Consumo
        if (pedido.getTipoPagamento().isPrePago()) {
            fundoConsumoService.estornar(pedidoId);
        }

        // Marca como estornado
        pedido.estornar();
        pedidoRepository.save(pedido);

        // TODO: Registrar evento de auditoria ESTORNO_MANUAL
        // eventLogService.registrarEventoFinanceiro(pedido, TipoEvento.ESTORNO_MANUAL, motivo, ...)

        log.info("Estorno concluído para pedido {} - Motivo: {}", pedidoId, motivo);
    }
}
