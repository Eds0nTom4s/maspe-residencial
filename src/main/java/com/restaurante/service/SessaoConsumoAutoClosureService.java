package com.restaurante.service;

import com.restaurante.consumo.participante.service.SessaoOwnerActionTokenService;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.model.enums.TipoSessao;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.service.operacional.OperationalEventLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SessaoConsumoAutoClosureService {

    private static final Logger log = LoggerFactory.getLogger(SessaoConsumoAutoClosureService.class);

    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final PedidoRepository pedidoRepository;
    private final OrdemPagamentoRepository ordemPagamentoRepository;
    private final OperationalEventLogService operationalEventLogService;
    private final SessaoOwnerActionTokenService ownerActionTokenService;

    public SessaoConsumoAutoClosureService(SessaoConsumoRepository sessaoConsumoRepository,
            PedidoRepository pedidoRepository,
            OrdemPagamentoRepository ordemPagamentoRepository,
            OperationalEventLogService operationalEventLogService,
            SessaoOwnerActionTokenService ownerActionTokenService) {
        this.sessaoConsumoRepository = sessaoConsumoRepository;
        this.pedidoRepository = pedidoRepository;
        this.ordemPagamentoRepository = ordemPagamentoRepository;
        this.operationalEventLogService = operationalEventLogService;
        this.ownerActionTokenService = ownerActionTokenService;
    }

    /**
     * Avalia e executa o encerramento automático da Sessão de Consumo.
     * Fecha automaticamente uma sessão quando todos os pedidos associados
     * estão terminais, todas as obrigações financeiras foram liquidadas e não
     * existem ordens de pagamento activas. A regra é conservadora e idempotente:
     * uma sessão com qualquer pendência permanece aberta.
     */
    @Transactional
    public void tryAutoCloseSessaoConsumo(Long sessaoId) {
        if (sessaoId == null) {
            return;
        }

        SessaoConsumo sessao = sessaoConsumoRepository.findById(sessaoId).orElse(null);
        if (sessao == null) {
            log.debug("Auto-closure ignorado: sessão {} não encontrada.", sessaoId);
            return;
        }

        // 1. Verificar estado atual (Idempotência)
        if (sessao.getStatus() == StatusSessaoConsumo.ENCERRADA || sessao.getStatus() == StatusSessaoConsumo.EXPIRADA) {
            log.debug("Auto-closure ignorado: sessão {} já encerrada ou expirada.", sessaoId);
            return;
        }

        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA
                && sessao.getStatus() != StatusSessaoConsumo.AGUARDANDO_PAGAMENTO) {
            log.debug("Auto-closure ignorado: sessão {} com status inválido: {}", sessaoId, sessao.getStatus());
            return;
        }

        // 2. Obter todos os pedidos, independentemente do template operacional.
        List<Pedido> pedidos = pedidoRepository.findBySessaoConsumoId(sessaoId, org.springframework.data.domain.Pageable.unpaged()).getContent();

        // Regra de segurança: não encerrar sessão vazia por esta via (deixar para o
        // scheduler expirar)
        if (pedidos.isEmpty()) {
            log.debug("Auto-closure ignorado: sessão {} sem pedidos.", sessaoId);
            return;
        }

        // 3. Avaliar pedidos e subpedidos
        for (Pedido p : pedidos) {
            if (p.getStatus() == StatusPedido.CRIADO || p.getStatus() == StatusPedido.EM_ANDAMENTO) {
                log.debug("Auto-closure ignorado: pedido {} pendente na sessão {}.", p.getId(), sessaoId);
                return;
            }

            // Não pode haver pendência de pagamento no pedido, a menos que esteja cancelado
            if (p.getStatus() != StatusPedido.CANCELADO) {
                if (p.getStatusFinanceiro() == StatusFinanceiroPedido.NAO_PAGO ||
                        p.getStatusFinanceiro() == StatusFinanceiroPedido.PENDENTE_PAGAMENTO) {
                    log.debug("Auto-closure ignorado: pedido {} com pendência financeira na sessão {}.", p.getId(),
                            sessaoId);
                    return;
                }
            }

            // Subpedidos pendentes (se o pedido não está cancelado, todos os subpedidos
            // devem ser ENTREGUE/CANCELADO)
            if (p.getStatus() != StatusPedido.CANCELADO) {
                if (p.getSubPedidos() != null) {
                    boolean hasPendingSub = p.getSubPedidos().stream()
                            .anyMatch(sub -> sub.getStatus() == null || !sub.getStatus().isTerminal());
                    if (hasPendingSub) {
                        log.debug("Auto-closure ignorado: subpedido pendente no pedido {} da sessão {}.", p.getId(),
                                sessaoId);
                        return;
                    }
                }
            }
        }

        // 4. Verificar Ordens de Pagamento Ativas
        boolean hasActiveOrders = ordemPagamentoRepository.existsBySessaoConsumoIdAndStatusIn(
                sessaoId, List.of(OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO));
        if (hasActiveOrders) {
            log.debug("Auto-closure ignorado: ordem de pagamento ativa na sessão {}.", sessaoId);
            return;
        }

        // 5. Encerrar
        log.info("Executando auto-closure da sessão {}. Todos os requisitos cumpridos.", sessaoId);

        StatusSessaoConsumo oldStatus = sessao.getStatus();
        sessao.encerrar(); // O método na entidade muda o status e encerra o FundoConsumo
        sessaoConsumoRepository.save(sessao);

        // Tokens de gestão da sessão deixam de ser válidos imediatamente.
        if (sessao.getTenant() != null) {
            try {
                ownerActionTokenService.revokeActiveTokensBySessao(
                        sessao.getTenant().getId(), sessao.getId(), "AUTO_SESSION_CLOSE", null, null
                );
            } catch (Exception ex) {
                log.warn("Falha ao revogar tokens da sessão {} durante auto-closure: {}", sessaoId, ex.getMessage());
            }
        }

        // 6. Auditoria
        if (sessao.getTenant() != null) {
            java.util.Map<String, Object> meta = new java.util.HashMap<>();
            meta.put("old_status", oldStatus.name());
            meta.put("new_status", sessao.getStatus().name());
            meta.put("total_pedidos", pedidos.size());
            meta.put("reason", "AUTO_CLOSURE_ALL_ORDERS_TERMINAL");

            operationalEventLogService.logGenericForTenant(
                    sessao.getTenant().getId(),
                    OperationalEventType.SESSAO_CONSUMO_ENCERRADA,
                    com.restaurante.model.enums.OperationalEntityType.SESSAO_CONSUMO,
                    sessao.getId(),
                    com.restaurante.model.enums.OperationalOrigem.SYSTEM,
                    "Auto encerramento por conclusão integral dos pedidos",
                    meta,
                    "127.0.0.1",
                    "SystemAutoClosureWorker");
        }
    }
}
