package com.restaurante.service.device.offline.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.financeiro.paymentmethod.service.PaymentMethodPolicyResolutionService;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodService;
import com.restaurante.financeiro.service.OrdemPagamentoService;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.FundoConsumo;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.PaymentDestination;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.FundoConsumoService;
import com.restaurante.service.device.offline.DeviceOfflineCommandProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CreateManualPaymentOrderOfflineHandler implements DeviceOfflineCommandHandler {

    private final ObjectMapper objectMapper;
    private final TenantRepository tenantRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final PedidoRepository pedidoRepository;
    private final TurnoOperacionalRepository turnoOperacionalRepository;
    private final FundoConsumoService fundoConsumoService;
    private final OrdemPagamentoService ordemPagamentoService;
    private final TenantPaymentMethodService tenantPaymentMethodService;
    private final PaymentMethodPolicyResolutionService policyResolutionService;

    @Override
    public DeviceOfflineCommandType type() {
        return DeviceOfflineCommandType.CREATE_ORDEM_PAGAMENTO_MANUAL;
    }

    @Override
    public DeviceOfflineCommandProcessor.ProcessedResult handle(DevicePrincipal device,
                                                                String commandClientRequestId,
                                                                JsonNode payload,
                                                                String derivedIdempotencyKey,
                                                                String ip,
                                                                String userAgent) {
        Long tenantId = device.tenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> notFound());
        Instituicao inst = instituicaoRepository.findById(device.instituicaoId()).orElseThrow(() -> notFound());
        UnidadeAtendimento unidade = unidadeAtendimentoRepository.findById(device.unidadeAtendimentoId()).orElseThrow(() -> notFound());
        dispositivoOperacionalRepository.findByIdAndTenantId(device.dispositivoId(), tenantId).orElseThrow(() -> notFound());

        if (payload == null) throw invalid("payload é obrigatório.");

        String destination = payload.hasNonNull("destination") ? payload.get("destination").asText() : null;
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
        PaymentMethodCode methodCode = manual == MetodoPagamentoManual.CASH ? PaymentMethodCode.CASH : PaymentMethodCode.TPA;

        TurnoOperacional turnoAberto = turnoOperacionalRepository
                .findOpenByTenantAndInstituicaoAndUnidade(tenantId, inst.getId(), unidade.getId())
                .orElse(null);
        var tenantMethod = tenantPaymentMethodService.getOrThrow(tenantId, methodCode);
        if (tenantMethod.isRequiresOpenTurno() && turnoAberto == null) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.TURNO_NOT_OPEN,
                    "Turno operacional aberto é obrigatório para este método.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                    null);
        }

        if ("PEDIDO".equalsIgnoreCase(destination)) {
            Long pedidoId = payload.hasNonNull("pedidoId") ? payload.get("pedidoId").asLong() : null;
            if (pedidoId == null) throw invalid("pedidoId é obrigatório para destination=PEDIDO");
            Pedido pedido = pedidoRepository.findByIdAndTenantId(pedidoId, tenantId).orElseThrow(() -> notFound());
            Long pedidoUaId = pedido.getSessaoConsumo() != null && pedido.getSessaoConsumo().getUnidadeAtendimento() != null
                    ? pedido.getSessaoConsumo().getUnidadeAtendimento().getId()
                    : null;
            if (pedidoUaId == null || !pedidoUaId.equals(unidade.getId())) throw notFound();

            BigDecimal valorPayload = payload.hasNonNull("valor") ? payload.get("valor").decimalValue() : null;
            if (valorPayload != null && pedido.getTotal() != null && pedido.getTotal().compareTo(valorPayload) != 0) {
                throw new DeviceApiException(HttpStatus.CONFLICT,
                        DeviceErrorResponse.DeviceErrorCode.PRICE_CHANGED,
                        "Valor do pedido mudou desde a criação offline.",
                        true,
                        DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                        Map.of("pedidoTotal", pedido.getTotal(), "localValor", valorPayload));
            }

            policyResolutionService.validateForDevice(device, methodCode, PaymentDestination.PEDIDO, pedido.getTotal());

            OrdemPagamento ordem = ordemPagamentoService.criarOrdemPagamentoPedido(
                    tenant, inst, unidade, null, turnoAberto, pedido, manual, OperationalOrigem.DEVICE_POS, ip, userAgent
            );
            return toResult(ordem);
        }

        if ("FUNDO_CONSUMO".equalsIgnoreCase(destination)) {
            Long sessaoId = payload.hasNonNull("sessaoConsumoId") ? payload.get("sessaoConsumoId").asLong() : null;
            if (sessaoId == null) throw invalid("sessaoConsumoId é obrigatório para destination=FUNDO_CONSUMO");
            BigDecimal valor = payload.hasNonNull("valor") ? payload.get("valor").decimalValue() : null;
            if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) throw invalid("valor inválido");

            SessaoConsumo sessao = sessaoConsumoRepository.findByIdAndTenantId(sessaoId, tenantId).orElseThrow(() -> notFound());
            if (sessao.getUnidadeAtendimento() == null || !sessao.getUnidadeAtendimento().getId().equals(unidade.getId())) throw notFound();
            if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) {
                throw new DeviceApiException(HttpStatus.CONFLICT,
                        DeviceErrorResponse.DeviceErrorCode.SESSION_CLOSED,
                        "Sessão não está ativa.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null);
            }

            FundoConsumo fundo = sessao.getFundoConsumo();
            if (fundo == null) fundo = fundoConsumoService.criarFundoParaSessao(sessao);

            policyResolutionService.validateForDevice(device, methodCode, PaymentDestination.FUNDO_CONSUMO, valor);

            OrdemPagamento ordem = ordemPagamentoService.criarOrdemCarregamentoFundo(
                    tenant, inst, unidade, null, turnoAberto, sessao, fundo, valor, manual, OperationalOrigem.DEVICE_POS, ip, userAgent
            );
            return toResult(ordem);
        }

        throw invalid("destination inválido");
    }

    private DeviceOfflineCommandProcessor.ProcessedResult toResult(OrdemPagamento ordem) {
        JsonNode result = objectMapper.valueToTree(Map.of(
                "ordemPagamentoId", ordem.getId(),
                "tipo", ordem.getTipo().name(),
                "status", ordem.getStatus().name(),
                "valor", ordem.getValor(),
                "moeda", ordem.getMoeda(),
                "metodoPagamento", ordem.getMetodoSolicitado().name(),
                "ordemToken", ordem.getTokenQr(),
                "expiresAt", ordem.getExpiresAt()
        ));
        return new DeviceOfflineCommandProcessor.ProcessedResult("ORDEM_PAGAMENTO", ordem.getId(), result);
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

    private DeviceApiException notFound() {
        return new DeviceApiException(HttpStatus.NOT_FOUND,
                DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                "Recurso não encontrado.",
                false,
                DeviceErrorResponse.DeviceRecoveryAction.NONE,
                null);
    }
}
