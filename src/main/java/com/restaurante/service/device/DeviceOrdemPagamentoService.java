package com.restaurante.service.device;

import com.restaurante.config.OperacaoProperties;
import com.restaurante.dto.request.ConfirmarOrdemManualRequest;
import com.restaurante.dto.response.ConfirmarOrdemManualResponse;
import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.dto.response.DeviceOrdemPagamentoResponse;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.financeiro.repository.OrdemPagamentoManualIdempotencyRepository;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodService;
import com.restaurante.financeiro.service.OrdemPagamentoService;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.OrdemPagamentoManualIdempotencyRecord;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.OrdemPagamentoManualIdempotencyStatus;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.OrdemPagamentoTipo;
import com.restaurante.model.enums.PaymentDestination;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentUsageContext;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.FundoConsumoService;
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
public class DeviceOrdemPagamentoService {

    private final OperacaoProperties operacaoProperties;
    private final TenantRepository tenantRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final TurnoOperacionalRepository turnoOperacionalRepository;
    private final OrdemPagamentoRepository ordemPagamentoRepository;
    private final OrdemPagamentoService ordemPagamentoService;
    private final OrdemPagamentoManualIdempotencyRepository idempotencyRepository;
    private final PagamentoGatewayRepository pagamentoGatewayRepository;
    private final FundoConsumoService fundoConsumoService;
    private final OperationalEventLogService operationalEventLogService;
    private final TenantPaymentMethodService tenantPaymentMethodService;

    @Transactional(readOnly = true)
    public DeviceOrdemPagamentoResponse escanearPorToken(String token) {
        DevicePrincipal device = requireDevicePrincipal();
        requireCapability(device, DeviceCapability.VIEW_PAYMENT_ORDER);

        OrdemPagamento ordem = ordemPagamentoRepository.findByTokenQr(token)
                .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Ordem não encontrada.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null));

        if (!ordem.getTenant().getId().equals(device.tenantId())) {
            throw new DeviceApiException(HttpStatus.NOT_FOUND,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "Ordem não encontrada.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
        if (ordem.getUnidadeAtendimento() != null && device.unidadeAtendimentoId() != null
                && !ordem.getUnidadeAtendimento().getId().equals(device.unidadeAtendimentoId())) {
            throw new DeviceApiException(HttpStatus.NOT_FOUND,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "Ordem não encontrada.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }

        DeviceOrdemPagamentoResponse resp = new DeviceOrdemPagamentoResponse();
        resp.setOrdemPagamentoId(ordem.getId());
        resp.setTipo(ordem.getTipo());
        resp.setStatus(ordem.getStatus());
        resp.setValor(ordem.getValor());
        resp.setMoeda(ordem.getMoeda());
        resp.setMetodoSolicitado(ordem.getMetodoSolicitado());
        resp.setCodigoConsumo(ordem.getSessaoConsumo() != null ? ordem.getSessaoConsumo().getQrCodeSessao() : null);
        resp.setPedidoId(ordem.getPedido() != null ? ordem.getPedido().getId() : null);

        boolean canConfirm = ordem.getStatus() == OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO
                && (ordem.getExpiresAt() == null || !ordem.isExpirada(LocalDateTime.now()));
        if (canConfirm && operacaoProperties.isRequireOpenTurnoForManualPayments()) {
            TurnoOperacional turnoAberto = turnoOperacionalRepository
                    .findOpenByTenantAndInstituicaoAndUnidade(device.tenantId(), device.instituicaoId(), device.unidadeAtendimentoId())
                    .orElse(null);
            canConfirm = turnoAberto != null;
        }
        resp.setPodeConfirmar(canConfirm);
        return resp;
    }

    @Transactional
    public ConfirmarOrdemManualResponse confirmarManual(Long ordemId,
                                                       ConfirmarOrdemManualRequest request,
                                                       String idempotencyKey,
                                                       String userAgent,
                                                       String ip) {
        DevicePrincipal device = requireDevicePrincipal();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "Idempotency-Key é obrigatório.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    null);
        }
        if (request == null || request.getClientRequestId() == null || request.getClientRequestId().isBlank()) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "clientRequestId é obrigatório.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    null);
        }
        if (request.getMetodoConfirmado() == null || request.getMetodoConfirmado() == MetodoPagamentoManual.APPYPAY) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "metodoConfirmado inválido. Use CASH ou TPA.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
        if (request.getMetodoConfirmado() == MetodoPagamentoManual.CASH) {
            requireCapability(device, DeviceCapability.CONFIRM_CASH_PAYMENT);
        } else if (request.getMetodoConfirmado() == MetodoPagamentoManual.TPA) {
            requireCapability(device, DeviceCapability.CONFIRM_TPA_PAYMENT);
        }

        DispositivoOperacional dispositivo = dispositivoOperacionalRepository.findById(device.dispositivoId())
                .orElseThrow(() -> new DeviceUnauthorizedException("Device inválido."));
        Tenant tenant = tenantRepository.findById(device.tenantId()).orElseThrow();

        OrdemPagamento ordemLocked = ordemPagamentoRepository.findForUpdateById(ordemId)
                .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Ordem não encontrada.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null));

        if (!ordemLocked.getTenant().getId().equals(device.tenantId())) {
            throw new DeviceApiException(HttpStatus.NOT_FOUND,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "Ordem não encontrada.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
        if (ordemLocked.getUnidadeAtendimento() != null && device.unidadeAtendimentoId() != null
                && !ordemLocked.getUnidadeAtendimento().getId().equals(device.unidadeAtendimentoId())) {
            throw new DeviceApiException(HttpStatus.NOT_FOUND,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "Ordem não encontrada.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }

        if (operacaoProperties.isRequireOpenTurnoForManualPayments()) {
            TurnoOperacional turnoAberto = turnoOperacionalRepository
                    .findOpenByTenantAndInstituicaoAndUnidade(device.tenantId(), device.instituicaoId(), device.unidadeAtendimentoId())
                    .orElse(null);
            if (turnoAberto == null) {
                throw new DeviceApiException(HttpStatus.CONFLICT,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Turno operacional aberto é obrigatório para confirmação manual.",
                        true,
                        DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                        null);
            }
            if (ordemLocked.getTurnoOperacional() != null && !ordemLocked.getTurnoOperacional().getId().equals(turnoAberto.getId())) {
                throw new DeviceApiException(HttpStatus.CONFLICT,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Ordem não pertence ao turno operacional aberto.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                        null);
            }
            ordemLocked.setTurnoOperacional(turnoAberto);
        }

        if (ordemLocked.getStatus() == OrdemPagamentoStatus.CONFIRMADA) {
            ConfirmarOrdemManualResponse replay = mapConfirmResponse(ordemLocked, request, null);
            replay.setIdempotentReplay(true);
            return replay;
        }

        String requestHash = computeRequestHash(device, ordemId, request);
        OrdemPagamentoManualIdempotencyState idem = beginOrReplayIdempotency(tenant, dispositivo, ordemLocked, idempotencyKey.trim(), request.getClientRequestId().trim(), requestHash, request);
        if (idem.replayResponse != null) return idem.replayResponse;

        // validações de negócio
        if (ordemLocked.getStatus() != OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO) {
            markFailed(idem.record, "INVALID_STATUS");
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "Ordem não pode ser confirmada no status: " + ordemLocked.getStatus(),
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
        if (ordemLocked.isExpirada(LocalDateTime.now())) {
            ordemLocked.setStatus(OrdemPagamentoStatus.EXPIRADA);
            ordemPagamentoRepository.save(ordemLocked);
            markFailed(idem.record, "EXPIRED");
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "Ordem expirada.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
        if (request.getValorRecebido() == null || request.getValorRecebido().compareTo(ordemLocked.getValor()) < 0) {
            markFailed(idem.record, "UNDERPAID");
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "valorRecebido menor que o valor da ordem.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
        if (request.getMetodoConfirmado() == MetodoPagamentoManual.TPA) {
            boolean hasRef = request.getReferenciaOperador() != null && !request.getReferenciaOperador().isBlank();
            boolean hasObs = request.getObservacao() != null && request.getObservacao().trim().length() >= 5;
            if (!hasRef && !hasObs) {
                markFailed(idem.record, "TPA_REF_REQUIRED");
                throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "TPA exige referenciaOperador ou observação mínima.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null);
            }
        }

        // Validar método tenant-aware no instante da confirmação (evita confirmar método desativado depois da criação)
        PaymentMethodCode methodCode = request.getMetodoConfirmado() == MetodoPagamentoManual.CASH ? PaymentMethodCode.CASH : PaymentMethodCode.TPA;
        PaymentDestination destination = ordemLocked.getTipo() == OrdemPagamentoTipo.FUNDO_CONSUMO ? PaymentDestination.FUNDO_CONSUMO : PaymentDestination.PEDIDO;
        try {
            tenantPaymentMethodService.validateMethodAllowed(
                    device.tenantId(),
                    methodCode,
                    PaymentUsageContext.DEVICE_POS,
                    destination,
                    ordemLocked.getValor()
            );
        } catch (RuntimeException e) {
            markFailed(idem.record, "PAYMENT_METHOD_INACTIVE_OR_NOT_ALLOWED");
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    e.getMessage(),
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                    null);
        }

        // aplica confirmação e efeitos (pagamento/pedido ou crédito fundo)
        ordemLocked.setConfirmadoPorDispositivo(dispositivo);
        Pagamento pagamento = ordemPagamentoService.aplicarConfirmacaoManualOrdem(
                ordemLocked,
                request.getMetodoConfirmado(),
                request.getValorRecebido(),
                request.getReferenciaOperador(),
                request.getObservacao()
        );

        completeIdempotency(idem.record);

        // auditoria (sem tokens/secrets)
        operationalEventLogService.logOrdemPagamentoEvent(
                OperationalEventType.ORDEM_PAGAMENTO_CONFIRMADA_MANUAL,
                ordemLocked,
                OperationalOrigem.DEVICE_POS,
                "Ordem confirmada manualmente",
                Map.of(
                        "ordemId", ordemLocked.getId(),
                        "tipo", ordemLocked.getTipo().name(),
                        "metodo", request.getMetodoConfirmado().name(),
                        "valor", ordemLocked.getValor(),
                        "deviceId", device.dispositivoId()
                ),
                ip,
                userAgent
        );

        if (ordemLocked.getTipo() == OrdemPagamentoTipo.FUNDO_CONSUMO) {
            operationalEventLogService.logOrdemPagamentoEvent(
                    OperationalEventType.FUNDO_CONSUMO_CREDITADO_MANUAL,
                    ordemLocked,
                    OperationalOrigem.DEVICE_POS,
                    "Fundo creditado por confirmação manual",
                    Map.of(
                            "ordemId", ordemLocked.getId(),
                            "metodo", request.getMetodoConfirmado().name(),
                            "valor", ordemLocked.getValor()
                    ),
                    ip,
                    userAgent
            );
        } else if (ordemLocked.getTipo() == OrdemPagamentoTipo.PEDIDO) {
            operationalEventLogService.logOrdemPagamentoEvent(
                    request.getMetodoConfirmado() == MetodoPagamentoManual.CASH
                            ? OperationalEventType.PAGAMENTO_CASH_CONFIRMADO_DEVICE
                            : OperationalEventType.PAGAMENTO_TPA_CONFIRMADO_DEVICE,
                    ordemLocked,
                    OperationalOrigem.DEVICE_POS,
                    "Pagamento manual confirmado",
                    Map.of(
                            "ordemId", ordemLocked.getId(),
                            "pedidoId", ordemLocked.getPedido() != null ? ordemLocked.getPedido().getId() : null,
                            "pagamentoId", pagamento != null ? pagamento.getId() : null,
                            "metodo", request.getMetodoConfirmado().name(),
                            "valor", ordemLocked.getValor()
                    ),
                    ip,
                    userAgent
            );
        }

        ConfirmarOrdemManualResponse resp = mapConfirmResponse(ordemLocked, request, pagamento);
        resp.setIdempotentReplay(false);
        return resp;
    }

    private ConfirmarOrdemManualResponse mapConfirmResponse(OrdemPagamento ordem, ConfirmarOrdemManualRequest request, Pagamento pagamento) {
        ConfirmarOrdemManualResponse resp = new ConfirmarOrdemManualResponse();
        resp.setOrdemPagamentoId(ordem.getId());
        resp.setTipo(ordem.getTipo());
        resp.setStatus(ordem.getStatus());
        resp.setMetodoConfirmado(request.getMetodoConfirmado());
        resp.setValor(ordem.getValor());
        resp.setValorRecebido(request.getValorRecebido());
        resp.setTroco(request.getValorRecebido() != null ? request.getValorRecebido().subtract(ordem.getValor()) : BigDecimal.ZERO);
        resp.setCodigoConsumo(ordem.getSessaoConsumo() != null ? ordem.getSessaoConsumo().getQrCodeSessao() : null);
        if (ordem.getSessaoConsumo() != null) {
            try {
                resp.setSaldoAtual(fundoConsumoService.consultarSaldoPorToken(ordem.getSessaoConsumo().getQrCodeSessao()));
            } catch (Exception ignored) { }
        }
        resp.setPedidoId(ordem.getPedido() != null ? ordem.getPedido().getId() : null);
        resp.setTurnoOperacionalId(ordem.getTurnoOperacional() != null ? ordem.getTurnoOperacional().getId() : null);
        resp.setConfirmadoPorDeviceId(ordem.getConfirmadoPorDispositivo() != null ? ordem.getConfirmadoPorDispositivo().getId() : null);
        resp.setConfirmadoEm(ordem.getConfirmadoEm());
        return resp;
    }

    private static String computeRequestHash(DevicePrincipal device, Long ordemId, ConfirmarOrdemManualRequest request) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String canonical = String.join("|",
                    String.valueOf(device.tenantId()),
                    String.valueOf(device.dispositivoId()),
                    String.valueOf(ordemId),
                    safe(request.getClientRequestId()),
                    request.getMetodoConfirmado() != null ? request.getMetodoConfirmado().name() : "",
                    request.getValorRecebido() != null ? request.getValorRecebido().toPlainString() : "",
                    safe(request.getReferenciaOperador()),
                    safe(request.getObservacao())
            );
            return HexFormat.of().formatHex(md.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular hash de idempotência.", e);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private OrdemPagamentoManualIdempotencyState beginOrReplayIdempotency(Tenant tenant,
                                                                         DispositivoOperacional dispositivo,
                                                                         OrdemPagamento ordem,
                                                                         String idempotencyKey,
                                                                         String clientRequestId,
                                                                         String requestHash,
                                                                         ConfirmarOrdemManualRequest request) {
        var byKey = idempotencyRepository.findByTenantIdAndDispositivoIdAndIdempotencyKey(tenant.getId(), dispositivo.getId(), idempotencyKey);
        if (byKey.isPresent()) {
            OrdemPagamentoManualIdempotencyRecord rec = byKey.get();
            if (!rec.getRequestHash().equals(requestHash)) {
                throw new DeviceApiException(HttpStatus.CONFLICT,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Idempotency-Key conflict.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null);
            }
            if (rec.getStatus() == OrdemPagamentoManualIdempotencyStatus.IN_PROGRESS) {
                throw new DeviceApiException(HttpStatus.CONFLICT,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Confirmação em andamento.",
                        true,
                        DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                        Map.of("code", "IN_PROGRESS"));
            }
            ConfirmarOrdemManualResponse replay = new ConfirmarOrdemManualResponse();
            replay.setOrdemPagamentoId(rec.getOrdemPagamento().getId());
            replay.setTipo(rec.getOrdemPagamento().getTipo());
            replay.setStatus(rec.getOrdemPagamento().getStatus());
            replay.setMetodoConfirmado(request.getMetodoConfirmado());
            replay.setValor(rec.getOrdemPagamento().getValor());
            replay.setValorRecebido(request.getValorRecebido());
            replay.setTroco(request.getValorRecebido().subtract(rec.getOrdemPagamento().getValor()));
            replay.setCodigoConsumo(rec.getOrdemPagamento().getSessaoConsumo() != null ? rec.getOrdemPagamento().getSessaoConsumo().getQrCodeSessao() : null);
            replay.setPedidoId(rec.getOrdemPagamento().getPedido() != null ? rec.getOrdemPagamento().getPedido().getId() : null);
            replay.setTurnoOperacionalId(rec.getOrdemPagamento().getTurnoOperacional() != null ? rec.getOrdemPagamento().getTurnoOperacional().getId() : null);
            replay.setConfirmadoPorDeviceId(rec.getOrdemPagamento().getConfirmadoPorDispositivo() != null ? rec.getOrdemPagamento().getConfirmadoPorDispositivo().getId() : null);
            replay.setConfirmadoEm(rec.getOrdemPagamento().getConfirmadoEm());
            replay.setIdempotentReplay(true);
            return new OrdemPagamentoManualIdempotencyState(rec, replay);
        }

        var byClient = idempotencyRepository.findByTenantIdAndDispositivoIdAndClientRequestId(tenant.getId(), dispositivo.getId(), clientRequestId);
        if (byClient.isPresent()) {
            OrdemPagamentoManualIdempotencyRecord rec = byClient.get();
            if (!rec.getRequestHash().equals(requestHash)) {
                throw new DeviceApiException(HttpStatus.CONFLICT,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "clientRequestId conflict.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null);
            }
            ConfirmarOrdemManualResponse replay = new ConfirmarOrdemManualResponse();
            replay.setOrdemPagamentoId(rec.getOrdemPagamento().getId());
            replay.setTipo(rec.getOrdemPagamento().getTipo());
            replay.setStatus(rec.getOrdemPagamento().getStatus());
            replay.setMetodoConfirmado(request.getMetodoConfirmado());
            replay.setValor(rec.getOrdemPagamento().getValor());
            replay.setValorRecebido(request.getValorRecebido());
            replay.setTroco(request.getValorRecebido().subtract(rec.getOrdemPagamento().getValor()));
            replay.setCodigoConsumo(rec.getOrdemPagamento().getSessaoConsumo() != null ? rec.getOrdemPagamento().getSessaoConsumo().getQrCodeSessao() : null);
            replay.setPedidoId(rec.getOrdemPagamento().getPedido() != null ? rec.getOrdemPagamento().getPedido().getId() : null);
            replay.setTurnoOperacionalId(rec.getOrdemPagamento().getTurnoOperacional() != null ? rec.getOrdemPagamento().getTurnoOperacional().getId() : null);
            replay.setConfirmadoPorDeviceId(rec.getOrdemPagamento().getConfirmadoPorDispositivo() != null ? rec.getOrdemPagamento().getConfirmadoPorDispositivo().getId() : null);
            replay.setConfirmadoEm(rec.getOrdemPagamento().getConfirmadoEm());
            replay.setIdempotentReplay(true);
            return new OrdemPagamentoManualIdempotencyState(rec, replay);
        }

        OrdemPagamentoManualIdempotencyRecord rec = new OrdemPagamentoManualIdempotencyRecord();
        rec.setTenant(tenant);
        rec.setDispositivo(dispositivo);
        rec.setOrdemPagamento(ordem);
        rec.setIdempotencyKey(idempotencyKey);
        rec.setClientRequestId(clientRequestId);
        rec.setRequestHash(requestHash);
        rec.setStatus(OrdemPagamentoManualIdempotencyStatus.IN_PROGRESS);
        try {
            rec = idempotencyRepository.save(rec);
        } catch (DataIntegrityViolationException e) {
            // corrida: reprocessar como replay
            return beginOrReplayIdempotency(tenant, dispositivo, ordem, idempotencyKey, clientRequestId, requestHash, request);
        }
        return new OrdemPagamentoManualIdempotencyState(rec, null);
    }

    private void completeIdempotency(OrdemPagamentoManualIdempotencyRecord rec) {
        rec.setStatus(OrdemPagamentoManualIdempotencyStatus.COMPLETED);
        idempotencyRepository.save(rec);
    }

    private void markFailed(OrdemPagamentoManualIdempotencyRecord rec, String errorCode) {
        rec.setStatus(OrdemPagamentoManualIdempotencyStatus.FAILED);
        rec.setErrorCode(errorCode);
        idempotencyRepository.save(rec);
    }

    private static class OrdemPagamentoManualIdempotencyState {
        final OrdemPagamentoManualIdempotencyRecord record;
        final ConfirmarOrdemManualResponse replayResponse;

        OrdemPagamentoManualIdempotencyState(OrdemPagamentoManualIdempotencyRecord record, ConfirmarOrdemManualResponse replayResponse) {
            this.record = record;
            this.replayResponse = replayResponse;
        }
    }

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        DevicePrincipal dp = principal instanceof DevicePrincipal p ? p : null;
        if (dp == null) throw new DeviceUnauthorizedException("Device não autenticado.");
        if (dp.tenantId() == null || dp.dispositivoId() == null) throw new DeviceUnauthorizedException("Device inválido.");
        return dp;
    }

    private void requireCapability(DevicePrincipal device, DeviceCapability capability) {
        if (device.capabilities() == null || !device.capabilities().contains(capability)) {
            throw new DeviceApiException(HttpStatus.FORBIDDEN,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_FORBIDDEN,
                    "Capability insuficiente: " + capability.name(),
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    Map.of("capability", capability.name()));
        }
    }
}
