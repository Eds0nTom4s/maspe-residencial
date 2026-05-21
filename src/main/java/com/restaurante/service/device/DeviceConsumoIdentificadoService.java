package com.restaurante.service.device;

import com.restaurante.consumo.identificacao.service.TelefoneNormalizerService;
import com.restaurante.dto.response.PublicRecuperacaoSessaoResumoResponse;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.FundoConsumoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceConsumoIdentificadoService {

    private final TelefoneNormalizerService phoneNormalizerService;
    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final FundoConsumoService fundoConsumoService;

    @Transactional(readOnly = true)
    public List<PublicRecuperacaoSessaoResumoResponse> listarSessoesAbertasPorTelefone(DevicePrincipal device, String rawPhone) {
        requireCapability(device);
        String normalized = phoneNormalizerService.normalizeOrThrow(rawPhone);
        Long unidadeId = device.unidadeAtendimentoId();

        List<SessaoConsumo> list = sessaoConsumoRepository.findOpenByTenantAndTelefoneIdentificado(
                device.tenantId(),
                normalized,
                unidadeId,
                StatusSessaoConsumo.ABERTA
        );

        return list.stream().limit(20).map(s -> {
            String unidade = s.getUnidadeAtendimento() != null ? s.getUnidadeAtendimento().getNome() : null;
            String saldo = null;
            try {
                saldo = fundoConsumoService.consultarSaldoPorToken(s.getQrCodeSessao()).toPlainString();
            } catch (Exception ignored) {
            }
            return new PublicRecuperacaoSessaoResumoResponse(s.getId(), s.getQrCodeSessao(), s.getAbertaEm(), unidade, saldo, s.getStatus().name());
        }).toList();
    }

    private void requireCapability(DevicePrincipal device) {
        if (device == null) throw new DeviceUnauthorizedException("DevicePrincipal ausente.");
        List<DeviceCapability> caps = device.capabilities();
        if (caps == null || (!caps.contains(DeviceCapability.MANAGE_CONSUMPTION_FUND) && !caps.contains(DeviceCapability.REPRINT_CONSUMPTION_DOCUMENTS))) {
            throw new DeviceUnauthorizedException("Device sem permissão para localizar sessão por telefone.");
        }
    }
}

