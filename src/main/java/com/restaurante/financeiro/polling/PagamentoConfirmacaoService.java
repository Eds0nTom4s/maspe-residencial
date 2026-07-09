package com.restaurante.financeiro.polling;

import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoEventoFinanceiro;
import com.restaurante.financeiro.repository.PagamentoEventLogRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.PagamentoEventLog;
import com.restaurante.model.entity.Pedido;
import com.restaurante.fiscal.autoissue.event.PaymentConfirmedForFiscalIssueEvent;
import com.restaurante.model.enums.FiscalAutoIssueSource;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.service.PedidoPagamentoPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Serviço central para aplicar confirmação vinda do gateway (callback ou polling).
 *
 * Objetivo: callback e polling convergem para a MESMA lógica, garantindo idempotência.
 */
@Service
@RequiredArgsConstructor
public class PagamentoConfirmacaoService {

    private final PagamentoGatewayRepository pagamentoRepository;
    private final PedidoRepository pedidoRepository;
    private final PagamentoEventLogRepository pagamentoEventLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PedidoPagamentoPolicy pedidoPagamentoPolicy;

    @Transactional
    public boolean confirmarPosPagoPorGateway(Long pagamentoId,
                                             String source,
                                             String actorRole,
                                             String ip,
                                             Long amountCentsOrNull) {
        Pagamento pagamento = pagamentoRepository.findForUpdateById(pagamentoId).orElseThrow();

        if (pagamento.getStatus() == StatusPagamentoGateway.CONFIRMADO) {
            return false; // idempotente
        }
        if (pagamento.getStatus() != StatusPagamentoGateway.PENDENTE) {
            return false; // não confirma fora do estado esperado
        }

        if (amountCentsOrNull != null) {
            long expected = toCentavos(pagamento.getAmount());
            if (!amountCentsOrNull.equals(expected)) {
                // Divergência: não confirmar aqui; caller decide registrar evento.
                throw new IllegalStateException("Valor divergente. Esperado=" + expected + " recebido=" + amountCentsOrNull);
            }
        }

        Pedido pedido = pagamento.getPedido();
        if (pedido == null) {
            throw new IllegalStateException("Pagamento POS_PAGO sem pedido vinculado.");
        }
        if (pedido.getTenant() == null || pagamento.getTenant() == null ||
                !pedido.getTenant().getId().equals(pagamento.getTenant().getId())) {
            throw new IllegalStateException("Tenant mismatch ao confirmar pagamento POS_PAGO.");
        }
        pedidoPagamentoPolicy.assertPodeConfirmarPagamento(pedido, PedidoPagamentoPolicy.PaymentFlow.GATEWAY_CONFIRMATION);

        pagamento.confirmar();
        pagamentoRepository.save(pagamento);

        if (pedido.getStatusFinanceiro() != StatusFinanceiroPedido.PAGO) {
            pedido.marcarComoPago();
            pedidoRepository.save(pedido);
        }

        PagamentoEventLog event = PagamentoEventLog.builder()
                .tipoEvento(TipoEventoFinanceiro.CONFIRMACAO_PAGAMENTO)
                .pagamento(pagamento)
                .pedido(pedido)
                .usuario(source)
                .role(actorRole)
                .ip(ip)
                .motivo("Pagamento confirmado por " + source)
                .build();
        pagamentoEventLogRepository.save(event);

        publishFiscalAutoIssueEvent(pagamento, pedido, source);
        return true;
    }

    private void publishFiscalAutoIssueEvent(Pagamento pagamento, Pedido pedido, String source) {
        if (pagamento == null || pedido == null) return;
        if (pagamento.getTenant() == null || pagamento.getTenant().getId() == null) return;
        if (pagamento.getId() == null || pedido.getId() == null) return;

        FiscalAutoIssueSource autoSource = mapSource(source);
        eventPublisher.publishEvent(new PaymentConfirmedForFiscalIssueEvent(
                pagamento.getTenant().getId(),
                pedido.getSessaoConsumo() != null && pedido.getSessaoConsumo().getUnidadeAtendimento() != null
                        ? pedido.getSessaoConsumo().getUnidadeAtendimento().getId()
                        : null,
                pedido.getId(),
                pagamento.getId(),
                pedido.getSessaoConsumo() != null ? pedido.getSessaoConsumo().getId() : null,
                null,
                autoSource
        ));
    }

    private static FiscalAutoIssueSource mapSource(String source) {
        if (source == null) return FiscalAutoIssueSource.SYSTEM_BACKFILL;
        if (source.contains("APPYPAY_CALLBACK")) return FiscalAutoIssueSource.APPYPAY_CALLBACK;
        if (source.contains("APPYPAY_POLLING")) return FiscalAutoIssueSource.APPYPAY_POLLING;
        return FiscalAutoIssueSource.SYSTEM_BACKFILL;
    }

    private static long toCentavos(BigDecimal amount) {
        if (amount == null) return 0L;
        return amount
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
