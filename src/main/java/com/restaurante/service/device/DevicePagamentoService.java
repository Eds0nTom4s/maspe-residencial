package com.restaurante.service.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.config.OperacaoProperties;
import com.restaurante.dto.request.DeviceIniciarPagamentoRequest;
import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.dto.response.DevicePagamentoResponse;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import com.restaurante.financeiro.gateway.appypay.AppyPayClient;
import com.restaurante.financeiro.gateway.appypay.AppyPayProperties;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeRequest;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodService;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.DevicePagamentoIdempotencyRecord;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DevicePagamentoIdempotencyStatus;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.PaymentDestination;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentUsageContext;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.repository.DevicePagamentoIdempotencyRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.PaymentReferenceService;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DevicePagamentoService {

    private final OperacaoProperties operacaoProperties;
    private final TenantRepository tenantRepository;
    private final PedidoRepository pedidoRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final TurnoOperacionalRepository turnoOperacionalRepository;
    private final PagamentoGatewayRepository pagamentoGatewayRepository;
    private final DevicePagamentoIdempotencyRepository idempotencyRepository;
    private final PaymentReferenceService paymentReferenceService;
    private final AppyPayClient appyPayClient;
    private final AppyPayProperties appyPayProperties;
    private final ObjectMapper objectMapper;
    private final OperationalEventLogService operationalEventLogService;
    private final TenantPaymentMethodService tenantPaymentMethodService;

    @Transactional
    public DevicePagamentoResponse iniciarPagamento(Long pedidoId,
                                                   DeviceIniciarPagamentoRequest request,
                                                   String idempotencyKey,
                                                   String userAgent,
                                                   String ip) {
        DevicePrincipal device = requireDevicePrincipal();
        requireCapability(device, DeviceCapability.INITIATE_PAYMENT);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key é obrigatório.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    null);
        }
        if (request == null || request.getClientRequestId() == null || request.getClientRequestId().isBlank()) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_CLIENT_REQUEST_ID_REQUIRED,
                    "clientRequestId é obrigatório.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    null);
        }
        if (request.getMetodoPagamento() == null) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_INVALID_METHOD,
                    "metodoPagamento é obrigatório.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    null);
        }

        Long tenantId = device.tenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new DeviceApiException(
                HttpStatus.NOT_FOUND,
                DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                "Recurso não encontrado.",
                false,
                DeviceErrorResponse.DeviceRecoveryAction.NONE,
                null
        ));

        DispositivoOperacional dispositivo = dispositivoOperacionalRepository.findByIdAndTenantId(device.dispositivoId(), tenantId)
                .orElseThrow(() -> new DeviceUnauthorizedException("Dispositivo inválido."));

        Pedido pedido = pedidoRepository.findByIdAndTenantId(pedidoId, tenantId)
                .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_PEDIDO_NOT_FOUND,
                        "Pedido não encontrado.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null));

        // Escopo por unidade do device
        Long pedidoUaId = pedido.getSessaoConsumo() != null && pedido.getSessaoConsumo().getUnidadeAtendimento() != null
                ? pedido.getSessaoConsumo().getUnidadeAtendimento().getId()
                : null;
        if (pedidoUaId == null || !pedidoUaId.equals(device.unidadeAtendimentoId())) {
            throw new DeviceApiException(HttpStatus.NOT_FOUND,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_PEDIDO_SCOPE_INVALID,
                    "Pedido não encontrado.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }

        if (pedido.getTotal() == null || pedido.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_VALIDATION_FAILED,
                    "Pedido inválido para pagamento.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                    null);
        }

        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_PEDIDO_ALREADY_PAID,
                    "Pedido já está pago.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }

        try {
            tenantPaymentMethodService.validateMethodAllowed(
                    tenantId,
                    PaymentMethodCode.APPYPAY,
                    PaymentUsageContext.DEVICE_POS,
                    PaymentDestination.PEDIDO,
                    pedido.getTotal()
            );
        } catch (RuntimeException e) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_VALIDATION_FAILED,
                    e.getMessage(),
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                    null);
        }

        if (pagamentoGatewayRepository.findPagamentoConfirmadoPorPedido(pedido.getId(), TipoPagamentoFinanceiro.POS_PAGO).isPresent()) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_PEDIDO_ALREADY_PAID,
                    "Pedido já está pago.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }

        boolean hasPending = pagamentoGatewayRepository.findByPedidoIdOrderByCreatedAtDesc(pedido.getId()).stream()
                .anyMatch(p -> p.getStatus() == StatusPagamentoGateway.PENDENTE);
        if (hasPending) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_ALREADY_PENDING,
                    "Já existe pagamento pendente para este pedido.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    null);
        }

        TurnoOperacional turnoAberto = turnoOperacionalRepository
                .findOpenByTenantAndInstituicaoAndUnidade(tenantId, device.instituicaoId(), device.unidadeAtendimentoId())
                .orElse(null);
        if (turnoAberto == null && operacaoProperties.isRequireOpenTurnoForDevicePayments()) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_TURNO_REQUIRED,
                    "Turno operacional aberto é obrigatório para iniciar pagamento no POS.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                    null);
        }
        if (pedido.getTurnoOperacional() != null && turnoAberto != null && !pedido.getTurnoOperacional().getId().equals(turnoAberto.getId())) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_INVALID_STATUS,
                    "Pedido não pertence ao turno operacional aberto.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                    null);
        }

        String requestHash = computeRequestHash(device, pedidoId, request);
        DevicePagamentoIdempotencyRecordState idem = beginOrReplayIdempotency(tenant, dispositivo, pedido, idempotencyKey.trim(), request.getClientRequestId().trim(), requestHash);
        if (idem.replayResponse != null) return idem.replayResponse;

        try {
            String externalRef = paymentReferenceService.gerarReferenciaPedidoDevice(tenant);

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
                    .observacoes(request.getDescricao() != null ? request.getDescricao() : ("Pagamento Pedido POS " + pedido.getNumero()))
                    .build();

            pagamento = pagamentoGatewayRepository.save(pagamento);

            // Marcar pedido como pendente de pagamento (se ainda não estiver)
            if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.NAO_PAGO) {
                pedido.setStatusFinanceiro(StatusFinanceiroPedido.PENDENTE_PAGAMENTO);
                pedidoRepository.save(pedido);
            }

            String returnUrl = request.getReturnUrl() != null && !request.getReturnUrl().isBlank()
                    ? request.getReturnUrl()
                    : appyPayProperties.getCallbackUrl();

            AppyPayChargeRequest chargeRequest = AppyPayChargeRequest.builder()
                    .merchantTransactionId(externalRef)
                    .amount(converterParaCentavos(pedido.getTotal()))
                    .paymentMethod(request.getMetodoPagamento().getCodigo())
                    .description(request.getDescricao() != null ? request.getDescricao() : ("Pedido " + pedido.getNumero()))
                    .returnUrl(returnUrl)
                    .callbackUrl(appyPayProperties.getCallbackUrl())
                    .mobileNumber(request.getTelefoneCliente())
                    .build();

            AppyPayChargeResponse response = appyPayClient.createCharge(chargeRequest);

            pagamento.setGatewayChargeId(response.getChargeId());
            pagamento.setGatewayResponse(serializarJson(response));
            if ("REF".equals(response.getPaymentMethod())) {
                pagamento.setEntidade(response.getEntity());
                pagamento.setReferencia(response.getReference());
            }
            pagamentoGatewayRepository.save(pagamento);

            completeIdempotency(idem.record, pagamento);

            operationalEventLogService.logTurnoEvent(
                    OperationalEventType.PAGAMENTO_INICIADO_DEVICE,
                    turnoAberto != null ? turnoAberto : pedido.getTurnoOperacional(),
                    OperationalOrigem.DEVICE_POS,
                    "Pagamento iniciado pelo POS",
                    Map.of(
                            "pedidoId", pedido.getId(),
                            "pagamentoId", pagamento.getId(),
                            "externalReference", pagamento.getExternalReference(),
                            "metodo", request.getMetodoPagamento().name(),
                            "deviceId", device.dispositivoId()
                    ),
                    ip,
                    userAgent
            );

            return toResponse(pedido, pagamento, false);
        } catch (DeviceApiException ex) {
            failIdempotency(idem.record, ex.getCode().name());
            throw ex;
        } catch (Exception ex) {
            failIdempotency(idem.record, "GATEWAY_ERROR");
            throw new DeviceApiException(HttpStatus.BAD_GATEWAY,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_GATEWAY_ERROR,
                    "Falha ao iniciar pagamento.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    null);
        }
    }

    private DevicePagamentoResponse toResponse(Pedido pedido, Pagamento pagamento, boolean replay) {
        DevicePagamentoResponse r = new DevicePagamentoResponse();
        r.setPagamentoId(pagamento.getId());
        r.setPedidoId(pedido.getId());
        r.setNumeroPedido(pedido.getNumero());
        r.setTenantId(pagamento.getTenant() != null ? pagamento.getTenant().getId() : null);
        Long instId = pedido.getSessaoConsumo() != null && pedido.getSessaoConsumo().getInstituicao() != null ? pedido.getSessaoConsumo().getInstituicao().getId() : null;
        Long uaId = pedido.getSessaoConsumo() != null && pedido.getSessaoConsumo().getUnidadeAtendimento() != null ? pedido.getSessaoConsumo().getUnidadeAtendimento().getId() : null;
        r.setInstituicaoId(instId);
        r.setUnidadeAtendimentoId(uaId);
        r.setTurnoOperacionalId(pedido.getTurnoOperacional() != null ? pedido.getTurnoOperacional().getId() : null);
        r.setValor(pagamento.getAmount());
        r.setMoeda("AOA");
        r.setMetodoPagamento(pagamento.getMetodo());
        r.setStatusPagamento(pagamento.getStatus());
        r.setGateway("APPYPAY");
        r.setExternalReference(pagamento.getExternalReference());
        r.setCheckoutUrl(extrairPaymentUrl(pagamento.getGatewayResponse()));
        r.setReferencia(pagamento.getReferencia());
        r.setEntidade(pagamento.getEntidade());
        r.setCriadoEm(pagamento.getCreatedAt());
        r.setIdempotentReplay(replay);
        return r;
    }

    private record DevicePagamentoIdempotencyRecordState(DevicePagamentoIdempotencyRecord record, DevicePagamentoResponse replayResponse) {}

    private DevicePagamentoIdempotencyRecordState beginOrReplayIdempotency(Tenant tenant,
                                                                          DispositivoOperacional dispositivo,
                                                                          Pedido pedido,
                                                                          String idemKey,
                                                                          String clientRequestId,
                                                                          String requestHash) {
        Long tenantId = tenant.getId();
        Long deviceId = dispositivo.getId();

        var byKey = idempotencyRepository.findByTenantIdAndDispositivoIdAndIdempotencyKey(tenantId, deviceId, idemKey);
        if (byKey.isPresent()) return handleExistingIdem(byKey.get(), requestHash);
        var byClient = idempotencyRepository.findByTenantIdAndDispositivoIdAndClientRequestId(tenantId, deviceId, clientRequestId);
        if (byClient.isPresent()) return handleExistingIdem(byClient.get(), requestHash);

        DevicePagamentoIdempotencyRecord rec = new DevicePagamentoIdempotencyRecord();
        rec.setTenant(tenant);
        rec.setDispositivo(dispositivo);
        rec.setPedido(pedido);
        rec.setIdempotencyKey(idemKey);
        rec.setClientRequestId(clientRequestId);
        rec.setRequestHash(requestHash);
        rec.setStatus(DevicePagamentoIdempotencyStatus.IN_PROGRESS);

        try {
            idempotencyRepository.saveAndFlush(rec);
            return new DevicePagamentoIdempotencyRecordState(rec, null);
        } catch (DataIntegrityViolationException ex) {
            var retry = idempotencyRepository.findByTenantIdAndDispositivoIdAndIdempotencyKey(tenantId, deviceId, idemKey)
                    .or(() -> idempotencyRepository.findByTenantIdAndDispositivoIdAndClientRequestId(tenantId, deviceId, clientRequestId))
                    .orElseThrow(() -> ex);
            return handleExistingIdem(retry, requestHash);
        }
    }

    private DevicePagamentoIdempotencyRecordState handleExistingIdem(DevicePagamentoIdempotencyRecord rec, String requestHash) {
        if (!requestHash.equals(rec.getRequestHash())) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_IDEMPOTENCY_CONFLICT,
                    "Conflito de idempotência: mesma chave/requestId com payload diferente.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                    null);
        }
        if (rec.getStatus() == DevicePagamentoIdempotencyStatus.COMPLETED && rec.getPagamento() != null) {
            Pagamento pg = pagamentoGatewayRepository.findById(rec.getPagamento().getId())
                    .orElseThrow(() -> new DeviceApiException(HttpStatus.CONFLICT,
                            DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_IDEMPOTENCY_CONFLICT,
                            "Registro idempotente inconsistente.",
                            true,
                            DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                            null));
            Pedido pedido = rec.getPedido();
            return new DevicePagamentoIdempotencyRecordState(rec, toResponse(pedido, pg, true));
        }
        if (rec.getStatus() == DevicePagamentoIdempotencyStatus.IN_PROGRESS) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_IDEMPOTENCY_CONFLICT,
                    "Pagamento em processamento para esta idempotencyKey/clientRequestId.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    null);
        }
        throw new DeviceApiException(HttpStatus.CONFLICT,
                DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_IDEMPOTENCY_CONFLICT,
                "Pagamento falhou anteriormente para esta idempotencyKey/clientRequestId.",
                true,
                DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                null);
    }

    private void completeIdempotency(DevicePagamentoIdempotencyRecord rec, Pagamento pagamento) {
        rec.setPagamento(pagamento);
        rec.setStatus(DevicePagamentoIdempotencyStatus.COMPLETED);
        rec.setErrorCode(null);
        idempotencyRepository.save(rec);
    }

    private void failIdempotency(DevicePagamentoIdempotencyRecord rec, String errorCode) {
        if (rec == null) return;
        rec.setStatus(DevicePagamentoIdempotencyStatus.FAILED);
        rec.setErrorCode(errorCode);
        idempotencyRepository.save(rec);
    }

    private long converterParaCentavos(BigDecimal valor) {
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

    private String computeRequestHash(DevicePrincipal device, Long pedidoId, DeviceIniciarPagamentoRequest req) {
        String payload = "tenantId=" + device.tenantId()
                + "|deviceId=" + device.dispositivoId()
                + "|pedidoId=" + pedidoId
                + "|clientRequestId=" + req.getClientRequestId()
                + "|metodo=" + (req.getMetodoPagamento() != null ? req.getMetodoPagamento().name() : "")
                + "|tel=" + (req.getTelefoneCliente() != null ? req.getTelefoneCliente() : "")
                + "|returnUrl=" + (req.getReturnUrl() != null ? req.getReturnUrl() : "");
        return sha256Hex(payload);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return Integer.toHexString(value != null ? value.hashCode() : 0);
        }
    }

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof DevicePrincipal dp)) {
            throw new DeviceUnauthorizedException("Dispositivo não autenticado.");
        }
        return (DevicePrincipal) auth.getPrincipal();
    }

    private void requireCapability(DevicePrincipal device, DeviceCapability capability) {
        if (device == null || device.capabilities() == null || !device.capabilities().contains(capability)) {
            throw new DeviceApiException(HttpStatus.FORBIDDEN,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_PAYMENT_CREATE_FORBIDDEN,
                    "Dispositivo sem permissão para iniciar pagamento.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                    null);
        }
    }
}
