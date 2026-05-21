package com.restaurante.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.PublicQrPagamentoRequest;
import com.restaurante.dto.response.PublicQrPagamentoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import com.restaurante.financeiro.gateway.appypay.AppyPayClient;
import com.restaurante.financeiro.gateway.appypay.AppyPayProperties;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeRequest;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodService;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.PaymentDestination;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentUsageContext;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PublicQrPagamentoService {

    private final QrCodeOperacionalService qrCodeOperacionalService;
    private final PedidoRepository pedidoRepository;
    private final PagamentoGatewayRepository pagamentoGatewayRepository;
    private final PaymentReferenceService paymentReferenceService;
    private final PublicQrPaymentIdempotencyService idempotencyService;
    private final AppyPayClient appyPayClient;
    private final AppyPayProperties appyPayProperties;
    private final ObjectMapper objectMapper;
    private final TenantPaymentMethodService tenantPaymentMethodService;

    @Transactional
    public PublicQrPagamentoResponse iniciarPagamentoPedidoPorQr(
            String qrToken,
            Long pedidoId,
            String idempotencyKeyHeader,
            PublicQrPagamentoRequest request
    ) {
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(qrToken);

        Tenant tenant = qr.getTenant();
        Pedido pedido = pedidoRepository.findByIdAndTenantId(pedidoId, tenant.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", "id", pedidoId));

        validarPedidoPertenceAoContextoQr(qr, pedido);

        if (pedido.getTotal() == null || pedido.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Pedido inválido para pagamento.");
        }
        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO) {
            throw new BusinessException("Pedido já está pago.");
        }

        tenantPaymentMethodService.validateMethodAllowed(
                tenant.getId(),
                PaymentMethodCode.APPYPAY,
                PaymentUsageContext.QR_PUBLICO,
                PaymentDestination.PEDIDO,
                pedido.getTotal()
        );

        String idemKey = idempotencyService.requireKey(idempotencyKeyHeader, request.getIdempotencyKey());
        String requestHash = idempotencyService.computeRequestHash(
                request.getMetodoPagamento() != null ? request.getMetodoPagamento().name() : null,
                request.getTelefone()
        );

        PublicQrPaymentIdempotencyService.StartResult start = idempotencyService.startOrGet(tenant, pedido, idemKey, requestHash);
        if (start.isCompleted()) {
            return mapPagamentoToResponse(pedido, start.completedPayment(), "Pagamento já iniciado anteriormente");
        }

        var idemReq = start.request();
        try {
            String externalRef = paymentReferenceService.gerarReferenciaPedidoQr(tenant);

            Pagamento pagamento = Pagamento.builder()
                    .tenant(tenant)
                    .pedido(pedido)
                    .fundoConsumo(null)
                    .cliente(null)
                    .tipoPagamento(TipoPagamentoFinanceiro.POS_PAGO)
                    .metodo(request.getMetodoPagamento())
                    .amount(pedido.getTotal())
                    .status(StatusPagamentoGateway.PENDENTE)
                    .externalReference(externalRef)
                    .observacoes("Pagamento Pedido QR " + pedido.getNumero())
                    .build();

            pagamento = pagamentoGatewayRepository.save(pagamento);

            // Marcar pedido como pendente de pagamento (se ainda não estiver pago)
            if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.NAO_PAGO) {
                pedido.setStatusFinanceiro(StatusFinanceiroPedido.PENDENTE_PAGAMENTO);
                pedidoRepository.save(pedido);
            }

            AppyPayChargeRequest chargeRequest = AppyPayChargeRequest.builder()
                    .merchantTransactionId(externalRef)
                    .amount(converterParaCentavos(pedido.getTotal()))
                    .paymentMethod(request.getMetodoPagamento().getCodigo())
                    .description("Pedido " + pedido.getNumero())
                    .returnUrl(appyPayProperties.getCallbackUrl())
                    .callbackUrl(appyPayProperties.getCallbackUrl())
                    .mobileNumber(request.getTelefone())
                    .build();

            AppyPayChargeResponse response = appyPayClient.createCharge(chargeRequest);

            pagamento.setGatewayChargeId(response.getChargeId());
            pagamento.setGatewayResponse(serializarJson(response));
            if ("REF".equals(response.getPaymentMethod())) {
                pagamento.setEntidade(response.getEntity());
                pagamento.setReferencia(response.getReference());
            }
            pagamentoGatewayRepository.save(pagamento);

            idempotencyService.markCompleted(idemReq, pagamento);
            return mapPagamentoToResponse(pedido, pagamento, "Pagamento iniciado");
        } catch (Exception e) {
            idempotencyService.markFailed(idemReq);
            if (e instanceof BusinessException) throw (BusinessException) e;
            throw new BusinessException("Falha ao iniciar pagamento: " + e.getMessage());
        }
    }

    private void validarPedidoPertenceAoContextoQr(QrCodeOperacional qr, Pedido pedido) {
        SessaoConsumo sessao = pedido.getSessaoConsumo();
        if (sessao == null) {
            throw new BusinessException("Pedido inválido para pagamento.");
        }

        Instituicao inst = sessao.getInstituicao();
        if (inst == null && sessao.getMesa() != null) {
            inst = sessao.getMesa().getInstituicao();
        }
        if (inst == null) {
            throw new BusinessException("Pedido inválido para pagamento.");
        }
        if (!inst.getId().equals(qr.getInstituicao().getId())) {
            throw new ResourceNotFoundException("Pedido", "id", pedido.getId());
        }

        if (qr.getMesa() != null) {
            Mesa mesa = sessao.getMesa();
            if (mesa == null || !mesa.getId().equals(qr.getMesa().getId())) {
                throw new ResourceNotFoundException("Pedido", "id", pedido.getId());
            }
        }

        if (qr.getUnidadeAtendimento() != null) {
            UnidadeAtendimento ua = sessao.getUnidadeAtendimento() != null ? sessao.getUnidadeAtendimento()
                    : (sessao.getMesa() != null ? sessao.getMesa().getUnidadeAtendimento() : null);
            if (ua == null || !ua.getId().equals(qr.getUnidadeAtendimento().getId())) {
                throw new ResourceNotFoundException("Pedido", "id", pedido.getId());
            }
        }
    }

    private PublicQrPagamentoResponse mapPagamentoToResponse(Pedido pedido, Pagamento pagamento, String mensagem) {
        return PublicQrPagamentoResponse.builder()
                .pagamentoId(pagamento.getId())
                .pedidoId(pedido.getId())
                .pedidoNumero(pedido.getNumero())
                .externalReference(pagamento.getExternalReference())
                .statusPagamento(pagamento.getStatus())
                .metodoPagamento(pagamento.getMetodo())
                .valor(pagamento.getAmount())
                .entidade(pagamento.getEntidade())
                .referencia(pagamento.getReferencia())
                .paymentUrl(extrairPaymentUrl(pagamento.getGatewayResponse()))
                .mensagem(mensagem)
                .build();
    }

    private Long converterParaCentavos(BigDecimal valor) {
        return valor.multiply(BigDecimal.valueOf(100)).longValue();
    }

    private String serializarJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String extrairPaymentUrl(String gatewayResponseJson) {
        if (gatewayResponseJson == null) return null;
        try {
            return objectMapper.readTree(gatewayResponseJson).path("paymentUrl").asText(null);
        } catch (Exception e) {
            return null;
        }
    }
}
