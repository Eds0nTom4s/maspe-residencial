package com.restaurante.service;

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

    public SessaoConsumoAutoClosureService(SessaoConsumoRepository sessaoConsumoRepository,
            PedidoRepository pedidoRepository,
            OrdemPagamentoRepository ordemPagamentoRepository,
            OperationalEventLogService operationalEventLogService) {
        this.sessaoConsumoRepository = sessaoConsumoRepository;
        this.pedidoRepository = pedidoRepository;
        this.ordemPagamentoRepository = ordemPagamentoRepository;
        this.operationalEventLogService = operationalEventLogService;
    }

    /**
     * Avalia e executa o encerramento automático da Sessão de Consumo.
     * Protege fluxos REST/KDS, fechando automaticamente apenas sessões
     * elegíveis de fluxo rápido (CONSUMA_PONTO), quando 100% das obrigações
     * estão cumpridas.
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

        // 2. Verificar template da unidade (Escopo exclusivo PONTO)
        if (sessao.getTenant() != null) {
            String template = sessao.getTenant().getTemplateCode();
            if (template != null && !template.equals("CONSUMA_PONTO_V1") && !template.equals("CONSUMA_PONTO_QR")) {
                log.debug("Auto-closure ignorado: template {} não elegível (apenas PONTO) na sessão {}.", template,
                        sessaoId);
                return;
            }
        }

        // 3. Obter todos os pedidos
        List<Pedido> pedidos = pedidoRepository.findBySessaoConsumoId(sessaoId, org.springframework.data.domain.Pageable.unpaged()).getContent();

        // Regra de segurança: não encerrar sessão vazia por esta via (deixar para o
        // scheduler expirar)
        if (pedidos.isEmpty()) {
            log.debug("Auto-closure ignorado: sessão {} sem pedidos.", sessaoId);
            return;
        }

        // 4. Avaliar pedidos e subpedidos
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
                            .anyMatch(sub -> sub.getStatus() == com.restaurante.model.enums.StatusSubPedido.PENDENTE ||
                                    sub.getStatus() == com.restaurante.model.enums.StatusSubPedido.EM_PREPARACAO);
                    if (hasPendingSub) {
                        log.debug("Auto-closure ignorado: subpedido pendente no pedido {} da sessão {}.", p.getId(),
                                sessaoId);
                        return;
                    }
                }
            }
        }

        // 5. Verificar Ordens de Pagamento Ativas
        boolean hasActiveOrders = ordemPagamentoRepository.existsBySessaoConsumoIdAndStatusIn(
                sessaoId, List.of(OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO));
        if (hasActiveOrders) {
            log.debug("Auto-closure ignorado: ordem de pagamento ativa na sessão {}.", sessaoId);
            return;
        }

        // 6. Encerrar
        log.info("Executando auto-closure da sessão {} (CONSUMA PONTO). Todos os requisitos cumpridos.", sessaoId);

        StatusSessaoConsumo oldStatus = sessao.getStatus();
        sessao.encerrar(); // O método na entidade muda o status e encerra o FundoConsumo
        sessaoConsumoRepository.save(sessao);

        // 7. Auditoria
        if (sessao.getTenant() != null) {
            java.util.Map<String, Object> meta = new java.util.HashMap<>();
            meta.put("old_status", oldStatus.name());
            meta.put("new_status", sessao.getStatus().name());
            meta.put("total_pedidos", pedidos.size());
            meta.put("reason", "AUTO_CLOSURE_CONSUMA_PONTO");

            operationalEventLogService.logGenericForTenant(
                    sessao.getTenant().getId(),
                    OperationalEventType.SESSAO_CONSUMO_ENCERRADA,
                    com.restaurante.model.enums.OperationalEntityType.SESSAO_CONSUMO,
                    sessao.getId(),
                    com.restaurante.model.enums.OperationalOrigem.SYSTEM,
                    "Auto encerramento por conclusão de pedidos PONTO",
                    meta,
                    "127.0.0.1",
                    "SystemAutoClosureWorker");
        }
    }
}
