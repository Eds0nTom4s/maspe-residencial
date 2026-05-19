package com.restaurante.financeiro.polling;

import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoEventoFinanceiro;
import com.restaurante.financeiro.repository.PagamentoEventLogRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.PagamentoEventLog;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
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

        return true;
    }

    private static long toCentavos(BigDecimal amount) {
        if (amount == null) return 0L;
        return amount
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}

