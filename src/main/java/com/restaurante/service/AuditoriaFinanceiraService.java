package com.restaurante.service;

import com.restaurante.model.entity.ConfiguracaoFinanceiraEventLog;
import com.restaurante.repository.ConfiguracaoFinanceiraEventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Serviço de auditoria financeira – persiste TODOS os eventos críticos em banco.
 *
 * <p>Princípio: auditoria não é opcional.  Qualquer ação que mova dinheiro,
 * altere limite ou mude política financeira DEVE chamar este serviço.
 *
 * <p>Os métodos usam {@code Propagation.REQUIRES_NEW} para garantir que o
 * registro de auditoria seja persistido mesmo que a transação chamadora seja
 * revertida (auditoria de tentativas também é relevante).
 *
 * <p>Eventos cobertos:
 * <ul>
 *   <li>ATIVOU_POS_PAGO / DESATIVOU_POS_PAGO</li>
 *   <li>ALTEROU_LIMITE_POS_PAGO</li>
 *   <li>ALTEROU_VALOR_MINIMO</li>
 *   <li>CONFIRMOU_PAGAMENTO_POS_PAGO</li>
 *   <li>ESTORNOU_PEDIDO</li>
 *   <li>AUTORIZOU_POS_PAGO (autorização individual de pedido)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditoriaFinanceiraService {

    private final ConfiguracaoFinanceiraEventLogRepository auditRepo;

    // ──────────────────────────────────────────────────────────────────────────
    // 1. EVENTOS DE CONFIGURAÇÃO GLOBAL
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Registra ativação ou desativação do interruptor de pós-pago.
     *
     * @param configId     ID da ConfiguracaoFinanceiraSistema alterada
     * @param valorAnterior flag antes da mudança
     * @param valorNovo    flag depois da mudança
     * @param usuarioNome  nome/login do operador
     * @param usuarioRole  role do operador
     * @param motivo       motivo declarado (pode ser nulo)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAlteracaoPosPagoAtivo(
            Long configId,
            boolean valorAnterior,
            boolean valorNovo,
            String usuarioNome,
            String usuarioRole,
            String motivo) {

        String tipoEvento = valorNovo ? "ATIVOU_POS_PAGO" : "DESATIVOU_POS_PAGO";

        ConfiguracaoFinanceiraEventLog evento = ConfiguracaoFinanceiraEventLog.builder()
                .tipoEvento(tipoEvento)
                .entidadeId(configId)
                .entidadeTipo("ConfiguracaoFinanceiraSistema")
                .usuarioNome(usuarioNome)
                .usuarioRole(usuarioRole)
                .flagAnterior(valorAnterior)
                .flagNovo(valorNovo)
                .motivo(motivo)
                .timestamp(LocalDateTime.now())
                .build();

        auditRepo.save(evento);
        log.info("🔐 AUDITORIA [{}] | posPagoAtivo: {} → {} | por {} ({}) | motivo: {}",
                tipoEvento, valorAnterior, valorNovo, usuarioNome, usuarioRole, motivo);
    }

    /**
     * Registra alteração do limite de pós-pago.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAlteracaoLimitePosPago(
            Long configId,
            BigDecimal limiteAnterior,
            BigDecimal limiteNovo,
            String usuarioNome,
            String usuarioRole,
            String motivo) {

        ConfiguracaoFinanceiraEventLog evento = ConfiguracaoFinanceiraEventLog.builder()
                .tipoEvento("ALTEROU_LIMITE_POS_PAGO")
                .entidadeId(configId)
                .entidadeTipo("ConfiguracaoFinanceiraSistema")
                .usuarioNome(usuarioNome)
                .usuarioRole(usuarioRole)
                .valorAnterior(limiteAnterior)
                .valorNovo(limiteNovo)
                .motivo(motivo)
                .timestamp(LocalDateTime.now())
                .build();

        auditRepo.save(evento);
        log.info("🔐 AUDITORIA [ALTEROU_LIMITE_POS_PAGO] | {} AOA → {} AOA | por {} ({}) | motivo: {}",
                limiteAnterior, limiteNovo, usuarioNome, usuarioRole, motivo);
    }

    /**
     * Registra alteração do valor mínimo de operação.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAlteracaoValorMinimo(
            Long configId,
            BigDecimal valorAnterior,
            BigDecimal valorNovo,
            String usuarioNome,
            String usuarioRole,
            String motivo) {

        ConfiguracaoFinanceiraEventLog evento = ConfiguracaoFinanceiraEventLog.builder()
                .tipoEvento("ALTEROU_VALOR_MINIMO")
                .entidadeId(configId)
                .entidadeTipo("ConfiguracaoFinanceiraSistema")
                .usuarioNome(usuarioNome)
                .usuarioRole(usuarioRole)
                .valorAnterior(valorAnterior)
                .valorNovo(valorNovo)
                .motivo(motivo)
                .timestamp(LocalDateTime.now())
                .build();

        auditRepo.save(evento);
        log.info("🔐 AUDITORIA [ALTEROU_VALOR_MINIMO] | {} AOA → {} AOA | por {} ({}) | motivo: {}",
                valorAnterior, valorNovo, usuarioNome, usuarioRole, motivo);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. EVENTOS DE PEDIDO / FINANCEIRO
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Registra confirmação de pagamento de pedido pós-pago.
     *
     * @param pedidoId    ID do Pedido confirmado
     * @param numeroPedido número legível do pedido
     * @param valor       valor confirmado
     * @param usuarioNome operador que confirmou
     * @param usuarioRole role do operador
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarConfirmacaoPagamentoPosPago(
            Long pedidoId,
            String numeroPedido,
            BigDecimal valor,
            String usuarioNome,
            String usuarioRole) {

        ConfiguracaoFinanceiraEventLog evento = ConfiguracaoFinanceiraEventLog.builder()
                .tipoEvento("CONFIRMOU_PAGAMENTO_POS_PAGO")
                .entidadeId(pedidoId)
                .entidadeTipo("Pedido")
                .usuarioNome(usuarioNome)
                .usuarioRole(usuarioRole)
                .valorNovo(valor)
                .detalhe("Pedido: " + numeroPedido)
                .timestamp(LocalDateTime.now())
                .build();

        auditRepo.save(evento);
        log.info("🔐 AUDITORIA [CONFIRMOU_PAGAMENTO_POS_PAGO] | Pedido {} ({} AOA) | por {} ({})",
                numeroPedido, valor, usuarioNome, usuarioRole);
    }

    /**
     * Registra estorno de pedido cancelado.
     *
     * @param pedidoId     ID do Pedido estornado
     * @param numeroPedido número legível
     * @param valor        valor estornado
     * @param usuarioNome  operador responsável
     * @param usuarioRole  role do operador
     * @param motivo       motivo obrigatório do cancelamento
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarEstornoPedido(
            Long pedidoId,
            String numeroPedido,
            BigDecimal valor,
            String usuarioNome,
            String usuarioRole,
            String motivo) {

        ConfiguracaoFinanceiraEventLog evento = ConfiguracaoFinanceiraEventLog.builder()
                .tipoEvento("ESTORNOU_PEDIDO")
                .entidadeId(pedidoId)
                .entidadeTipo("Pedido")
                .usuarioNome(usuarioNome)
                .usuarioRole(usuarioRole)
                .valorAnterior(valor)
                .motivo(motivo)
                .detalhe("Pedido: " + numeroPedido)
                .timestamp(LocalDateTime.now())
                .build();

        auditRepo.save(evento);
        log.info("🔐 AUDITORIA [ESTORNOU_PEDIDO] | Pedido {} ({} AOA) | por {} ({}) | motivo: {}",
                numeroPedido, valor, usuarioNome, usuarioRole, motivo);
    }

    /**
     * Registra autorização individual de pedido pós-pago.
     * Chamado pelo motor de confirmação automática.
     *
     * @param pedidoId           ID do Pedido
     * @param numeroPedido       número legível
     * @param valor              valor do pedido
     * @param unidadeConsumoId   ID da unidade de consumo
     * @param usuarioNome        operador/sistema que autorizou
     * @param usuarioRole        role
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAutorizacaoPosPago(
            Long pedidoId,
            String numeroPedido,
            BigDecimal valor,
            Long unidadeConsumoId,
            String usuarioNome,
            String usuarioRole) {

        ConfiguracaoFinanceiraEventLog evento = ConfiguracaoFinanceiraEventLog.builder()
                .tipoEvento("AUTORIZOU_POS_PAGO")
                .entidadeId(pedidoId)
                .entidadeTipo("Pedido")
                .usuarioNome(usuarioNome != null ? usuarioNome : "system")
                .usuarioRole(usuarioRole != null ? usuarioRole : "SYSTEM")
                .valorNovo(valor)
                .detalhe(String.format("Pedido: %s | UnidadeConsumo: %d", numeroPedido, unidadeConsumoId))
                .timestamp(LocalDateTime.now())
                .build();

        auditRepo.save(evento);
        log.info("🔐 AUDITORIA [AUTORIZOU_POS_PAGO] | Pedido {} ({} AOA) | UnidadeConsumo {}",
                numeroPedido, valor, unidadeConsumoId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. CONSULTAS DE AUDITORIA
    // ──────────────────────────────────────────────────────────────────────────

    /** Últimos 50 eventos financeiros (para dashboard de compliance). */
    @Transactional(readOnly = true)
    public List<ConfiguracaoFinanceiraEventLog> buscarUltimosEventos() {
        return auditRepo.findUltimosEventos(50);
    }

    /** Eventos de um operador específico. */
    @Transactional(readOnly = true)
    public List<ConfiguracaoFinanceiraEventLog> buscarPorOperador(String usuarioNome) {
        return auditRepo.findByUsuarioNomeOrderByTimestampDesc(usuarioNome);
    }

    /** Eventos por tipo. */
    @Transactional(readOnly = true)
    public List<ConfiguracaoFinanceiraEventLog> buscarPorTipo(String tipoEvento) {
        return auditRepo.findByTipoEventoOrderByTimestampDesc(tipoEvento);
    }
}
