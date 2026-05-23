package com.restaurante.service.device.offline.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ConfirmarOrdemManualRequest;
import com.restaurante.dto.response.ConfirmarOrdemManualResponse;
import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.device.DeviceOrdemPagamentoService;
import com.restaurante.service.device.offline.DeviceOfflineCommandProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConfirmManualPaymentOfflineHandler implements DeviceOfflineCommandHandler {

    private final ObjectMapper objectMapper;
    private final DeviceOrdemPagamentoService deviceOrdemPagamentoService;

    @Override
    public DeviceOfflineCommandType type() {
        return DeviceOfflineCommandType.CONFIRM_MANUAL_PAYMENT;
    }

    @Override
    public DeviceOfflineCommandProcessor.ProcessedResult handle(DevicePrincipal device,
                                                                String commandClientRequestId,
                                                                JsonNode payload,
                                                                String derivedIdempotencyKey,
                                                                String ip,
                                                                String userAgent) {
        if (payload == null) throw invalid("payload é obrigatório");
        Long ordemId = payload.hasNonNull("ordemPagamentoId") ? payload.get("ordemPagamentoId").asLong() : null;
        if (ordemId == null) throw invalid("ordemPagamentoId é obrigatório");

        String metodo = payload.hasNonNull("metodo") ? payload.get("metodo").asText() : null;
        MetodoPagamentoManual manual = safeManual(metodo);
        if (manual == null || manual == MetodoPagamentoManual.APPYPAY) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.OFFLINE_COMMAND_TYPE_NOT_ALLOWED,
                    "Método inválido para offline: use CASH ou TPA.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }

        ConfirmarOrdemManualRequest req = new ConfirmarOrdemManualRequest();
        req.setClientRequestId(commandClientRequestId);
        req.setMetodoConfirmado(manual);
        if (payload.hasNonNull("valorConfirmado")) req.setValorRecebido(payload.get("valorConfirmado").decimalValue());
        if (payload.hasNonNull("comprovativoLocal")) req.setReferenciaOperador(payload.get("comprovativoLocal").asText());
        if (payload.hasNonNull("observacao")) req.setObservacao(payload.get("observacao").asText());

        ConfirmarOrdemManualResponse resp = deviceOrdemPagamentoService.confirmarManual(ordemId, req, derivedIdempotencyKey, userAgent, ip);
        JsonNode result = objectMapper.valueToTree(resp);
        return new DeviceOfflineCommandProcessor.ProcessedResult("ORDEM_PAGAMENTO", resp.getOrdemPagamentoId(), result);
    }

    private MetodoPagamentoManual safeManual(String raw) {
        try {
            return raw != null ? MetodoPagamentoManual.valueOf(raw) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private DeviceApiException invalid(String msg) {
        return new DeviceApiException(HttpStatus.BAD_REQUEST,
                DeviceErrorResponse.DeviceErrorCode.OFFLINE_PAYLOAD_INVALID,
                msg,
                true,
                DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                null);
    }
}

