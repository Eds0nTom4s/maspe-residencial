package com.restaurante.store.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.exception.BusinessException;
import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import com.restaurante.financeiro.gateway.appypay.AppyPayClient;
import com.restaurante.financeiro.gateway.appypay.AppyPayProperties;
import com.restaurante.financeiro.gateway.appypay.AppyPayStatusMapper;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeRequest;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.store.dto.StoreCheckoutRequest;
import com.restaurante.store.model.StoreOrderMetadata;
import com.restaurante.store.repository.StoreOrderMetadataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class StorePaymentService {

    private final AppyPayClient appyPayClient;
    private final PagamentoGatewayRepository pagamentoRepository;
    private final StoreOrderMetadataRepository metadataRepository;
    private final PedidoRepository pedidoRepository;
    private final SubPedidoRepository subPedidoRepository;
    private final AppyPayProperties appyPayProperties;
    private final ObjectMapper objectMapper;

    public StorePaymentService(AppyPayClient appyPayClient,
                               PagamentoGatewayRepository pagamentoRepository,
                               StoreOrderMetadataRepository metadataRepository,
                               PedidoRepository pedidoRepository,
                               SubPedidoRepository subPedidoRepository,
                               AppyPayProperties appyPayProperties,
                               ObjectMapper objectMapper) {
        this.appyPayClient = appyPayClient;
        this.pagamentoRepository = pagamentoRepository;
        this.metadataRepository = metadataRepository;
        this.pedidoRepository = pedidoRepository;
        this.subPedidoRepository = subPedidoRepository;
        this.appyPayProperties = appyPayProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Pagamento initiatePayment(Pedido pedido, StoreOrderMetadata metadata,
                                     StoreCheckoutRequest.MetodoPagamentoLoja metodoPagamento,
                                     String telefone) {
        MetodoPagamentoAppyPay metodo = switch (metodoPagamento) {
            case APPYPAY_GPO, WALLET -> MetodoPagamentoAppyPay.GPO;
            case APPYPAY_REF -> MetodoPagamentoAppyPay.REF;
        };

        String externalRef = generateExternalReference();
        AppyPayChargeRequest request = AppyPayChargeRequest.builder()
                .merchantTransactionId(externalRef)
                .amount(toMinorUnits(pedido.getTotal()))
                .paymentMethod(metodo.getCodigo())
                .description("Ordem Loja GDSE " + pedido.getNumero())
                .mobileNumber(telefone)
                .callbackUrl(appyPayProperties.getCallbackUrl())
                .returnUrl(appyPayProperties.getReturnUrl())
                .build();

        AppyPayChargeResponse response = appyPayClient.createCharge(request);
        StatusPagamentoGateway status = AppyPayStatusMapper.toGatewayStatus(response.getStatus());

        Pagamento pagamento = Pagamento.builder()
                .pedido(pedido)
                .cliente(pedido.getSessaoConsumo() != null ? pedido.getSessaoConsumo().getCliente() : null)
                .tipoPagamento(TipoPagamentoFinanceiro.STORE_PEDIDO)
                .metodo(metodo)
                .amount(pedido.getTotal())
                .status(status)
                .externalReference(externalRef)
                .gatewayChargeId(response.getChargeId())
                .entidade(response.getEntity())
                .referencia(response.getReference())
                .confirmedAt(status == StatusPagamentoGateway.CONFIRMADO ? LocalDateTime.now() : null)
                .gatewayResponse(serialize(response))
                .observacoes("Pagamento direto da Loja GDSE")
                .build();

        pagamento = pagamentoRepository.save(pagamento);

        metadata.setMetodoPagamento(metodoPagamento.name());
        metadata.setPaymentUrl(response.getPaymentUrl());
        metadata.setEntidade(response.getEntity());
        metadata.setReferencia(response.getReference());
        metadataRepository.save(metadata);

        if (status == StatusPagamentoGateway.CONFIRMADO) {
            confirmStorePayment(pagamento);
        }

        return pagamento;
    }

    @Transactional
    public void confirmStorePayment(Pagamento pagamento) {
        if (pagamento.isConfirmado() && pagamento.getPedido().isPago()) {
            return;
        }
        if (!pagamento.isConfirmado()) {
            pagamento.confirmar();
            pagamentoRepository.save(pagamento);
        }

        Pedido pedido = pagamento.getPedido();
        pedido.setStatusFinanceiro(StatusFinanceiroPedido.PAGO);
        pedido.setPagoEm(LocalDateTime.now());
        pedido.getSubPedidos().forEach(subPedido -> {
            if (subPedido.getStatus() == StatusSubPedido.CRIADO || subPedido.getStatus() == StatusSubPedido.PENDENTE) {
                subPedido.setStatus(StatusSubPedido.EM_PREPARACAO);
                subPedido.setIniciadoEm(LocalDateTime.now());
                subPedidoRepository.save(subPedido);
            }
        });
        pedidoRepository.save(pedido);
    }

    @Transactional
    public void confirmStorePaymentByExternalReference(String externalReference) {
        Pagamento pagamento = pagamentoRepository.findByExternalReference(externalReference)
                .orElseThrow(() -> new BusinessException("Pagamento da loja não encontrado"));
        if (metadataRepository.existsByPedidoId(pagamento.getPedido().getId())) {
            confirmStorePayment(pagamento);
        }
    }

    private Long toMinorUnits(BigDecimal value) {
        return value.multiply(BigDecimal.valueOf(100)).longValue();
    }

    private String generateExternalReference() {
        long timestamp = System.currentTimeMillis() % 100000000L;
        int random = (int) (Math.random() * 10000);
        return String.format("ST%08d%04d", timestamp, random);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
