package com.restaurante.fiscal.autoissue.listener;

import com.restaurante.fiscal.autoissue.event.PaymentConfirmedForFiscalIssueEvent;
import com.restaurante.fiscal.autoissue.service.FiscalAutoIssueJobService;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.FiscalAutoIssueSource;
import com.restaurante.financeiro.caixa.repository.CaixaOperadorSessionRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
@RequiredArgsConstructor
public class FiscalAutoIssueOnPaymentConfirmedListener {

    private final PedidoRepository pedidoRepository;
    private final PagamentoGatewayRepository pagamentoRepository;
    private final CaixaOperadorSessionRepository caixaRepository;
    private final FiscalAutoIssueJobService jobService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentConfirmed(PaymentConfirmedForFiscalIssueEvent event) {
        Pagamento pagamento = pagamentoRepository.findById(event.pagamentoId()).orElse(null);
        if (pagamento == null) return;
        Pedido pedido = pedidoRepository.findById(event.pedidoId()).orElse(null);
        if (pedido == null) return;

        Tenant tenant = pagamento.getTenant();
        if (tenant == null) return;
        if (!tenant.getId().equals(event.tenantId())) return;

        UnidadeAtendimento unidade = pedido.getSessaoConsumo() != null ? pedido.getSessaoConsumo().getUnidadeAtendimento() : null;
        SessaoConsumo sessao = pedido.getSessaoConsumo();
        CaixaOperadorSession caixa = event.caixaOperadorSessionIdOrNull() != null
                ? caixaRepository.findById(event.caixaOperadorSessionIdOrNull()).orElse(null)
                : null;
        FiscalAutoIssueSource source = event.source() != null ? event.source() : FiscalAutoIssueSource.SYSTEM_BACKFILL;

        jobService.createAutoOnPaymentConfirmedJobIfEligible(
                tenant,
                unidade,
                pedido,
                pagamento,
                sessao,
                caixa,
                source
        );
    }
}
