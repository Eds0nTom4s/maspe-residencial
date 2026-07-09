package com.restaurante.service.device.offline.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.DeviceCriarPedidoItemRequest;
import com.restaurante.dto.request.DeviceCriarPedidoRequest;
import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.dto.response.DevicePedidoResponse;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.device.DevicePedidoService;
import com.restaurante.service.device.offline.DeviceOfflineCommandProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CreatePedidoPosOfflineHandler implements DeviceOfflineCommandHandler {

    private final ObjectMapper objectMapper;
    private final ProdutoRepository produtoRepository;
    private final DevicePedidoService devicePedidoService;

    @Override
    public DeviceOfflineCommandType type() {
        return DeviceOfflineCommandType.CREATE_PEDIDO_POS;
    }

    @Override
    public DeviceOfflineCommandProcessor.ProcessedResult handle(DevicePrincipal device,
                                                                String commandClientRequestId,
                                                                JsonNode payload,
                                                                String derivedIdempotencyKey,
                                                                String ip,
                                                                String userAgent) {
        Long tenantId = device.tenantId();

        BigDecimal localTotalEstimado = payload != null && payload.hasNonNull("localTotalEstimado")
                ? payload.get("localTotalEstimado").decimalValue()
                : null;

        DeviceCriarPedidoRequest req = new DeviceCriarPedidoRequest();
        req.setClientRequestId(commandClientRequestId);
        if (payload != null && payload.hasNonNull("mesaId")) req.setMesaId(payload.get("mesaId").asLong());
        if (payload != null && payload.hasNonNull("qrCodeId")) req.setQrCodeId(payload.get("qrCodeId").asLong());
        if (payload != null && payload.hasNonNull("observacaoPedido")) req.setObservacao(payload.get("observacaoPedido").asText());

        List<DeviceCriarPedidoItemRequest> itens = new ArrayList<>();
        JsonNode itensNode = payload != null ? payload.get("itens") : null;
        if (itensNode == null || !itensNode.isArray() || itensNode.isEmpty()) {
            throw invalid("itens é obrigatório.");
        }
        for (JsonNode it : itensNode) {
            DeviceCriarPedidoItemRequest item = new DeviceCriarPedidoItemRequest();
            if (it.hasNonNull("produtoId")) item.setProdutoId(it.get("produtoId").asLong());
            if (it.hasNonNull("quantidade")) item.setQuantidade(it.get("quantidade").asInt());
            if (it.hasNonNull("observacao")) item.setObservacao(it.get("observacao").asText());
            itens.add(item);
        }
        req.setItens(itens);

        if (localTotalEstimado != null) {
            BigDecimal computed = computePedidoTotalFromCatalog(tenantId, itensNode);
            if (computed != null && computed.compareTo(localTotalEstimado) != 0) {
                throw new DeviceApiException(HttpStatus.CONFLICT,
                        DeviceErrorResponse.DeviceErrorCode.PRICE_CHANGED,
                        "Preço alterado desde a criação offline (recalcule e reenvie).",
                        true,
                        DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                        Map.of("serverTotal", computed, "localTotal", localTotalEstimado));
            }
        }

        DevicePedidoResponse resp = devicePedidoService.criarPedido(req, derivedIdempotencyKey, userAgent, ip);
        JsonNode result = objectMapper.valueToTree(resp);
        return new DeviceOfflineCommandProcessor.ProcessedResult("PEDIDO", resp.getPedidoId(), result);
    }

    private BigDecimal computePedidoTotalFromCatalog(Long tenantId, JsonNode itensNode) {
        if (tenantId == null || itensNode == null || !itensNode.isArray()) return null;
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode it : itensNode) {
            if (!it.hasNonNull("produtoId") || !it.hasNonNull("quantidade")) continue;
            long produtoId = it.get("produtoId").asLong();
            int qtd = it.get("quantidade").asInt();
            var prod = produtoRepository.findByIdAndTenantId(produtoId, tenantId).orElse(null);
            if (prod == null) return null;
            if (!Boolean.TRUE.equals(prod.getAtivo()) || !Boolean.TRUE.equals(prod.getDisponivel())) return null;
            total = total.add(prod.getPreco().multiply(BigDecimal.valueOf(qtd)));
        }
        return total;
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

