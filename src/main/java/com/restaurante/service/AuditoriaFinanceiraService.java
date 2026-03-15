package com.restaurante.service;

import com.restaurante.model.entity.ConfiguracaoFinanceiraEventLog;
import com.restaurante.repository.ConfiguracaoFinanceiraEventLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class AuditoriaFinanceiraService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaFinanceiraService.class);

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

        ConfiguracaoFinanceiraEventLog evento = new ConfiguracaoFinanceiraEventLog();
        evento.setTipoEvento(tipoEvento);
        evento.setEntidadeId(configId);
        evento.setEntidadeTipo("ConfiguracaoFinanceiraSistema");
        evento.setUsuarioNome(usuarioNome);
        evento.setUsuarioRole(usuarioRole);
        evento.setFlagAnterior(valorAnterior);
        evento.setFlagNovo(valorNovo);
        evento.setMotivo(motivo);
        evento.setTimestamp(LocalDateTime.now());

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

        ConfiguracaoFinanceiraEventLog evento = new ConfiguracaoFinanceiraEventLog();
        evento.setTipoEvento("ALTEROU_LIMITE_POS_PAGO");
        evento.setEntidadeId(configId);
        evento.setEntidadeTipo("ConfiguracaoFinanceiraSistema");
        evento.setUsuarioNome(usuarioNome);
        evento.setUsuarioRole(usuarioRole);
        evento.setValorAnterior(limiteAnterior);
        evento.setValorNovo(limiteNovo);
        evento.setMotivo(motivo);
        evento.setTimestamp(LocalDateTime.now());

        auditRepo.save(evento);
        log.info("🔐 AUDITORIA [ALTEROU_LIMITE_POS_PAGO] | {} → {} | por {} ({}) | motivo: {}",
                com.restaurante.util.MoneyFormatter.format(limiteAnterior), com.restaurante.util.MoneyFormatter.format(limiteNovo), usuarioNome, usuarioRole, motivo);
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

        ConfiguracaoFinanceiraEventLog evento = new ConfiguracaoFinanceiraEventLog();
        evento.setTipoEvento("ALTEROU_VALOR_MINIMO");
        evento.setEntidadeId(configId);
        evento.setEntidadeTipo("ConfiguracaoFinanceiraSistema");
        evento.setUsuarioNome(usuarioNome);
        evento.setUsuarioRole(usuarioRole);
        evento.setValorAnterior(valorAnterior);
        evento.setValorNovo(valorNovo);
        evento.setMotivo(motivo);
        evento.setTimestamp(LocalDateTime.now());

        auditRepo.save(evento);
        log.info("🔐 AUDITORIA [ALTEROU_VALOR_MINIMO] | {} → {} | por {} ({}) | motivo: {}",
                com.restaurante.util.MoneyFormatter.format(valorAnterior), com.restaurante.util.MoneyFormatter.format(valorNovo), usuarioNome, usuarioRole, motivo);
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

        ConfiguracaoFinanceiraEventLog evento = new ConfiguracaoFinanceiraEventLog();
        evento.setTipoEvento("CONFIRMOU_PAGAMENTO_POS_PAGO");
        evento.setEntidadeId(pedidoId);
        evento.setEntidadeTipo("Pedido");
        evento.setUsuarioNome(usuarioNome);
        evento.setUsuarioRole(usuarioRole);
        evento.setValorNovo(valor);
        evento.setDetalhe("Pedido: " + numeroPedido);
        evento.setTimestamp(LocalDateTime.now());

        auditRepo.save(evento);
        log.info("🔐 AUDITORIA [CONFIRMOU_PAGAMENTO_POS_PAGO] | Pedido {} ({}) | por {} ({})",
                numeroPedido, com.restaurante.util.MoneyFormatter.format(valor), usuarioNome, usuarioRole);
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

        ConfiguracaoFinanceiraEventLog evento = new ConfiguracaoFinanceiraEventLog();
        evento.setTipoEvento("ESTORNOU_PEDIDO");
        evento.setEntidadeId(pedidoId);
        evento.setEntidadeTipo("Pedido");
        evento.setUsuarioNome(usuarioNome);
        evento.setUsuarioRole(usuarioRole);
        evento.setValorAnterior(valor);
        evento.setMotivo(motivo);
        evento.setDetalhe("Pedido: " + numeroPedido);
        evento.setTimestamp(LocalDateTime.now());

        auditRepo.save(evento);
        log.info("🔐 AUDITORIA [ESTORNOU_PEDIDO] | Pedido {} ({}) | por {} ({}) | motivo: {}",
                numeroPedido, com.restaurante.util.MoneyFormatter.format(valor), usuarioNome, usuarioRole, motivo);
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

        ConfiguracaoFinanceiraEventLog evento = new ConfiguracaoFinanceiraEventLog();
        evento.setTipoEvento("AUTORIZOU_POS_PAGO");
        evento.setEntidadeId(pedidoId);
        evento.setEntidadeTipo("Pedido");
        evento.setUsuarioNome(usuarioNome != null ? usuarioNome : "system");
        evento.setUsuarioRole(usuarioRole != null ? usuarioRole : "SYSTEM");
        evento.setValorNovo(valor);
        evento.setDetalhe(String.format("Pedido: %s | UnidadeConsumo: %d", numeroPedido, unidadeConsumoId));
        evento.setTimestamp(LocalDateTime.now());

        auditRepo.save(evento);
        log.info("🔐 AUDITORIA [AUTORIZOU_POS_PAGO] | Pedido {} ({}) | UnidadeConsumo {}",
                numeroPedido, com.restaurante.util.MoneyFormatter.format(valor), unidadeConsumoId);
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
