package com.restaurante.service.device;

import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.dto.response.ReimpressaoDocumentoResponse;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.FundoConsumoService;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceConsumoDocumentService {

    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final FundoConsumoService fundoConsumoService;
    private final OrdemPagamentoRepository ordemPagamentoRepository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional(readOnly = true)
    public ReimpressaoDocumentoResponse reimprimirQrConsumo(String codigoConsumo, String ip, String userAgent) {
        DevicePrincipal device = requireDevicePrincipal();
        requireCapability(device, DeviceCapability.REPRINT_CONSUMPTION_DOCUMENTS);

        SessaoConsumo sessao = sessaoConsumoRepository.findByTenantIdAndQrCodeSessao(device.tenantId(), codigoConsumo)
                .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Consumo não encontrado.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null));

        ReimpressaoDocumentoResponse resp = new ReimpressaoDocumentoResponse();
        resp.setTipoDocumento("CONSUMO_QR");
        resp.setCodigo(codigoConsumo);
        resp.setQrCodePayload(codigoConsumo);
        resp.setConteudoImprimivel("CONSUMA\nCONSUMO: " + codigoConsumo + "\nApresente este QR no balcão.");
        resp.setEmitidoEm(LocalDateTime.now());

        operationalEventLogService.logSessaoConsumoEvent(
                OperationalEventType.CONSUMO_QR_REIMPRESSO_DEVICE,
                sessao,
                OperationalOrigem.DEVICE_POS,
                "Reimpressão lógica de QR de consumo",
                Map.of("codigoConsumo", codigoConsumo),
                ip,
                userAgent
        );

        return resp;
    }

    @Transactional(readOnly = true)
    public ReimpressaoDocumentoResponse reimprimirConta(String codigoConsumo, String ip, String userAgent) {
        DevicePrincipal device = requireDevicePrincipal();
        requireCapability(device, DeviceCapability.REPRINT_CONSUMPTION_DOCUMENTS);

        SessaoConsumo sessao = sessaoConsumoRepository.findByTenantIdAndQrCodeSessao(device.tenantId(), codigoConsumo)
                .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Consumo não encontrado.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null));

        var saldo = fundoConsumoService.consultarSaldoPorToken(codigoConsumo);

        ReimpressaoDocumentoResponse resp = new ReimpressaoDocumentoResponse();
        resp.setTipoDocumento("CONTA_CONSUMO");
        resp.setCodigo(codigoConsumo);
        resp.setQrCodePayload(null);
        resp.setConteudoImprimivel("CONSUMA\nCONTA CONSUMO: " + codigoConsumo + "\nSaldo atual: " + saldo + " AOA");
        resp.setEmitidoEm(LocalDateTime.now());

        operationalEventLogService.logSessaoConsumoEvent(
                OperationalEventType.CONTA_REIMPRESSA_DEVICE,
                sessao,
                OperationalOrigem.DEVICE_POS,
                "Reimpressão lógica de conta de consumo",
                Map.of("codigoConsumo", codigoConsumo, "saldo", saldo),
                ip,
                userAgent
        );

        return resp;
    }

    @Transactional(readOnly = true)
    public ReimpressaoDocumentoResponse reimprimirComprovativoOrdem(Long ordemId, String ip, String userAgent) {
        DevicePrincipal device = requireDevicePrincipal();
        requireCapability(device, DeviceCapability.REPRINT_CONSUMPTION_DOCUMENTS);

        OrdemPagamento ordem = ordemPagamentoRepository.findById(ordemId)
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

        ReimpressaoDocumentoResponse resp = new ReimpressaoDocumentoResponse();
        resp.setTipoDocumento("COMPROVATIVO_ORDEM_PAGAMENTO");
        resp.setCodigo(ordem.getCodigoCurto());
        resp.setQrCodePayload(ordem.getTokenQr());
        resp.setConteudoImprimivel("CONSUMA\nCOMPROVATIVO ORDEM: " + ordem.getCodigoCurto()
                + "\nTipo: " + ordem.getTipo()
                + "\nValor: " + ordem.getValor() + " " + ordem.getMoeda()
                + "\nStatus: " + ordem.getStatus());
        resp.setEmitidoEm(LocalDateTime.now());

        operationalEventLogService.logOrdemPagamentoEvent(
                OperationalEventType.COMPROVATIVO_ORDEM_REIMPRESSO_DEVICE,
                ordem,
                OperationalOrigem.DEVICE_POS,
                "Reimpressão lógica de comprovativo da ordem",
                Map.of("ordemId", ordem.getId(), "codigoCurto", ordem.getCodigoCurto()),
                ip,
                userAgent
        );

        return resp;
    }

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        DevicePrincipal dp = principal instanceof DevicePrincipal p ? p : null;
        if (dp == null) throw new DeviceUnauthorizedException("Device não autenticado.");
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

