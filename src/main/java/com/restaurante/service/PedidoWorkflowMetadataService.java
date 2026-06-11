package com.restaurante.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.consumo.identificacao.entity.ClienteConsumo;
import com.restaurante.consumo.identificacao.service.TelefoneNormalizerService;
import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.PedidoEventLog;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.repository.PedidoEventLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PedidoWorkflowMetadataService {

    private final OrdemPagamentoRepository ordemPagamentoRepository;
    private final PagamentoGatewayRepository pagamentoGatewayRepository;
    private final PedidoEventLogRepository pedidoEventLogRepository;
    private final TelefoneNormalizerService telefoneNormalizerService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PedidoWorkflowMetadata resolve(Pedido pedido) {
        if (pedido == null || pedido.getId() == null) {
            return PedidoWorkflowMetadata.empty();
        }

        ClienteConsumo cliente = pedido.getClienteConsumo();
        Optional<OrdemPagamento> ordemOpt = ordemPagamentoRepository.findFirstByPedidoIdOrderByCreatedAtDesc(pedido.getId());
        Pagamento pagamento = pagamentoGatewayRepository.findByPedidoIdOrderByCreatedAtDesc(pedido.getId())
                .stream()
                .findFirst()
                .orElse(null);
        Optional<PedidoEventLog> aceite = pedidoEventLogRepository.findFirstByPedidoIdAndStatusNovoOrderByTimestampDesc(
                pedido.getId(), StatusPedido.EM_ANDAMENTO
        );
        Optional<PedidoEventLog> rejeicao = pedidoEventLogRepository.findFirstByPedidoIdAndStatusNovoOrderByTimestampDesc(
                pedido.getId(), StatusPedido.CANCELADO
        );

        PaymentMethodCode metodoPagamento = null;
        String metodoPagamentoDetalhe = null;
        String ordemPagamentoToken = null;
        String ordemPagamentoStatus = null;
        String entidade = null;
        String referencia = null;
        String paymentUrl = null;

        if (pagamento != null && pagamento.getMetodo() != null) {
            metodoPagamento = PaymentMethodCode.APPYPAY;
            metodoPagamentoDetalhe = pagamento.getMetodo().name();
            entidade = pagamento.getEntidade();
            referencia = pagamento.getReferencia();
            paymentUrl = extrairPaymentUrl(pagamento.getGatewayResponse());
        }

        if (ordemOpt.isPresent()) {
            OrdemPagamento ordem = ordemOpt.get();
            ordemPagamentoToken = ordem.getTokenQr();
            ordemPagamentoStatus = ordem.getStatus() != null ? ordem.getStatus().name() : null;
            if (metodoPagamento == null && ordem.getMetodoSolicitado() != null) {
                metodoPagamento = switch (ordem.getMetodoSolicitado()) {
                    case CASH -> PaymentMethodCode.CASH;
                    case TPA -> PaymentMethodCode.TPA;
                    case APPYPAY -> PaymentMethodCode.APPYPAY;
                };
                metodoPagamentoDetalhe = ordem.getMetodoSolicitado().name();
            }
        }

        if (metodoPagamento == null && pagamento != null && pagamento.getMetodo() == null) {
            metodoPagamento = PaymentMethodCode.APPYPAY;
            metodoPagamentoDetalhe = MetodoPagamentoAppyPay.GPO.name();
        }

        return PedidoWorkflowMetadata.builder()
                .clienteNome(cliente != null ? cliente.getNome() : null)
                .clienteTelefoneMascarado(maskTelefone(cliente))
                .metodoPagamento(metodoPagamento)
                .metodoPagamentoDetalhe(metodoPagamentoDetalhe)
                .motivoRejeicao(rejeicao.map(PedidoEventLog::getObservacoes).orElse(null))
                .ordemPagamentoToken(ordemPagamentoToken)
                .ordemPagamentoStatus(ordemPagamentoStatus)
                .entidade(entidade)
                .referencia(referencia)
                .paymentUrl(paymentUrl)
                .aceiteEm(aceite.map(PedidoEventLog::getTimestamp).orElse(null))
                .rejeitadoEm(rejeicao.map(PedidoEventLog::getTimestamp).orElse(null))
                .build();
    }

    private String maskTelefone(ClienteConsumo cliente) {
        if (cliente == null || cliente.getTelefoneNormalizado() == null || cliente.getTelefoneNormalizado().isBlank()) {
            return null;
        }
        return telefoneNormalizerService.mask(cliente.getTelefoneNormalizado());
    }

    private String extrairPaymentUrl(String gatewayResponseJson) {
        if (gatewayResponseJson == null || gatewayResponseJson.isBlank()) {
            return null;
        }
        try {
            JsonNode json = objectMapper.readTree(gatewayResponseJson);
            return json.path("paymentUrl").asText(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    @lombok.Builder
    public record PedidoWorkflowMetadata(
            String clienteNome,
            String clienteTelefoneMascarado,
            PaymentMethodCode metodoPagamento,
            String metodoPagamentoDetalhe,
            String motivoRejeicao,
            String ordemPagamentoToken,
            String ordemPagamentoStatus,
            String entidade,
            String referencia,
            String paymentUrl,
            LocalDateTime aceiteEm,
            LocalDateTime rejeitadoEm
    ) {
        static PedidoWorkflowMetadata empty() {
            return PedidoWorkflowMetadata.builder().build();
        }
    }
}
