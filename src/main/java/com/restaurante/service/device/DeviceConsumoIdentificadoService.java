package com.restaurante.service.device;

import com.restaurante.consumo.identificacao.entity.ClienteConsumo;
import com.restaurante.consumo.identificacao.entity.TelefoneOtpChallenge;
import com.restaurante.consumo.identificacao.service.ClienteConsumoService;
import com.restaurante.consumo.identificacao.service.TelefoneNormalizerService;
import com.restaurante.consumo.identificacao.service.TelefoneOtpService;
import com.restaurante.dto.response.PublicRecuperacaoSessaoResumoResponse;
import com.restaurante.device.capability.service.DeviceCapabilityService;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.OtpPurpose;
import com.restaurante.model.enums.SessaoIdentificacaoStatus;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.FundoConsumoService;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceConsumoIdentificadoService {

    private final TelefoneNormalizerService phoneNormalizerService;
    private final TelefoneOtpService otpService;
    private final ClienteConsumoService clienteConsumoService;
    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final FundoConsumoService fundoConsumoService;
    private final OperationalEventLogService operationalEventLogService;
    private final DeviceCapabilityService deviceCapabilityService;

    @Transactional(readOnly = true)
    public List<PublicRecuperacaoSessaoResumoResponse> listarSessoesAbertasPorTelefone(DevicePrincipal device, String rawPhone, String ip, String userAgent) {
        deviceCapabilityService.require(device, DeviceCapability.LOOKUP_CONSUMPTION_BY_PHONE, "LOOKUP_CONSUMPTION_BY_PHONE", ip, userAgent);
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

    @Transactional
    public TelefoneOtpService.OtpRequestResult requestOtpVinculacaoAssistida(DevicePrincipal device,
                                                                             Long sessaoId,
                                                                             String rawPhone,
                                                                             String clientIp,
                                                                             String userAgent) {
        deviceCapabilityService.require(device, DeviceCapability.REQUEST_ASSISTED_IDENTIFICATION_OTP, "REQUEST_ASSISTED_IDENTIFICATION_OTP", clientIp, userAgent);
        SessaoConsumo sessao = sessaoConsumoRepository.findByIdAndTenantId(sessaoId, device.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("SessaoConsumo", "id", sessaoId));
        validateSessaoForAssistido(device, sessao, clientIp, userAgent);

        TelefoneOtpService.OtpRequestResult result = otpService.requestOtp(
                sessao.getTenant(),
                rawPhone,
                OtpPurpose.POS_VINCULAR_SESSAO,
                sessao,
                clientIp,
                userAgent
        );

        operationalEventLogService.logSessaoConsumoEvent(
                OperationalEventType.SESSAO_CONSUMO_IDENTIFICACAO_POS_REQUESTED,
                sessao,
                OperationalOrigem.DEVICE_POS,
                "OTP solicitado (assistido POS)",
                Map.of(
                        "tenantId", device.tenantId(),
                        "unidadeId", device.unidadeAtendimentoId(),
                        "deviceId", device.dispositivoId(),
                        "challengeId", result.getChallenge().getId(),
                        "telefoneMascarado", result.getMaskedPhone(),
                        "purpose", OtpPurpose.POS_VINCULAR_SESSAO.name(),
                        "smsSent", result.isSmsSent()
                ),
                clientIp,
                userAgent
        );
        return result;
    }

    @Transactional
    public AssistidoVerifyResult verifyOtpVinculacaoAssistida(DevicePrincipal device,
                                                             Long sessaoId,
                                                             Long challengeId,
                                                             String rawPhone,
                                                             String otp,
                                                             String clientIp,
                                                             String userAgent) {
        deviceCapabilityService.requireAll(
                device,
                List.of(DeviceCapability.VERIFY_ASSISTED_IDENTIFICATION_OTP, DeviceCapability.LINK_CUSTOMER_TO_SESSION),
                "VERIFY_AND_LINK_ASSISTED_IDENTIFICATION",
                clientIp,
                userAgent
        );

        SessaoConsumo sessao = sessaoConsumoRepository.findByIdAndTenantIdForUpdate(sessaoId, device.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("SessaoConsumo", "id", sessaoId));
        validateSessaoForAssistido(device, sessao, clientIp, userAgent);

        String phoneNormalized = phoneNormalizerService.normalizeOrThrow(rawPhone);
        String masked = phoneNormalizerService.mask(phoneNormalized);

        if (sessao.getClienteConsumo() != null) {
            String existingPhone = sessao.getTelefoneIdentificado();
            if (existingPhone != null && existingPhone.equals(phoneNormalized)) {
                return new AssistidoVerifyResult(
                        sessao.getClienteConsumo().getId(),
                        sessao.getId(),
                        masked,
                        sessao.getIdentificacaoStatus(),
                        sessao.isIdentificadoPorOtp(),
                        sessao.getTelefoneIdentificadoEm(),
                        "JA_IDENTIFICADA"
                );
            }
            throw new BusinessException("SESSAO_CONSUMO_ALREADY_IDENTIFIED");
        }

        TelefoneOtpChallenge challenge;
        try {
            challenge = otpService.validateOtpPendingOrThrow(device.tenantId(), challengeId, rawPhone, otp);
        } catch (BusinessException e) {
            operationalEventLogService.logSessaoConsumoEvent(
                    OperationalEventType.SESSAO_CONSUMO_IDENTIFICACAO_POS_FAILED,
                    sessao,
                    OperationalOrigem.DEVICE_POS,
                    "Falha OTP assistido",
                    Map.of(
                            "tenantId", device.tenantId(),
                            "unidadeId", device.unidadeAtendimentoId(),
                            "deviceId", device.dispositivoId(),
                            "challengeId", challengeId,
                            "telefoneMascarado", masked,
                            "purpose", OtpPurpose.POS_VINCULAR_SESSAO.name(),
                            "error", e.getMessage()
                    ),
                    clientIp,
                    userAgent
            );
            throw e;
        }

        if (challenge.getPurpose() != OtpPurpose.POS_VINCULAR_SESSAO) throw new BusinessException("OTP_CHALLENGE_PURPOSE_INVALID");
        if (challenge.getSessaoConsumo() == null || challenge.getSessaoConsumo().getId() == null || !challenge.getSessaoConsumo().getId().equals(sessaoId)) {
            throw new BusinessException("OTP_CHALLENGE_SESSION_MISMATCH");
        }

        ClienteConsumoService.GetOrCreateResult created = clienteConsumoService.getOrCreateByPhone(sessao.getTenant(), rawPhone, phoneNormalized);
        ClienteConsumo cliente = created.cliente();
        if (created.created()) {
            operationalEventLogService.logSessaoConsumoEvent(
                    OperationalEventType.CLIENTE_CONSUMO_CREATED,
                    sessao,
                    OperationalOrigem.DEVICE_POS,
                    "ClienteConsumo criado (POS assistido)",
                    Map.of("tenantId", device.tenantId(), "clienteConsumoId", cliente.getId(), "telefoneMascarado", masked, "deviceId", device.dispositivoId()),
                    clientIp,
                    userAgent
            );
        }

        boolean wasVerified = cliente.isTelefoneVerificado();
        cliente = clienteConsumoService.markPhoneVerified(cliente);
        if (!wasVerified) {
            operationalEventLogService.logSessaoConsumoEvent(
                    OperationalEventType.CLIENTE_CONSUMO_PHONE_VERIFIED,
                    sessao,
                    OperationalOrigem.DEVICE_POS,
                    "Telefone verificado por OTP (POS assistido)",
                    Map.of("tenantId", device.tenantId(), "clienteConsumoId", cliente.getId(), "telefoneMascarado", masked, "deviceId", device.dispositivoId()),
                    clientIp,
                    userAgent
            );
        }

        // vincula sessão
        sessao.setClienteConsumo(cliente);
        sessao.setTelefoneIdentificado(phoneNormalized);
        sessao.setTelefoneIdentificadoEm(Instant.now());
        sessao.setIdentificacaoStatus(SessaoIdentificacaoStatus.IDENTIFICADA);
        sessao.setIdentificadoPorOtp(true);
        sessaoConsumoRepository.save(sessao);

        // consome challenge somente depois da vinculação bem-sucedida (mesma transação)
        otpService.consumeChallenge(device.tenantId(), challengeId);

        operationalEventLogService.logSessaoConsumoEvent(
                OperationalEventType.SESSAO_CONSUMO_IDENTIFICACAO_POS_VERIFIED,
                sessao,
                OperationalOrigem.DEVICE_POS,
                "Sessão identificada (POS assistido)",
                Map.of(
                        "tenantId", device.tenantId(),
                        "unidadeId", device.unidadeAtendimentoId(),
                        "deviceId", device.dispositivoId(),
                        "clienteConsumoId", cliente.getId(),
                        "telefoneMascarado", masked,
                        "challengeId", challengeId,
                        "purpose", OtpPurpose.POS_VINCULAR_SESSAO.name()
                ),
                clientIp,
                userAgent
        );
        operationalEventLogService.logSessaoConsumoEvent(
                OperationalEventType.SESSAO_CONSUMO_IDENTIFICADA,
                sessao,
                OperationalOrigem.DEVICE_POS,
                "Sessão identificada por telefone+OTP (POS assistido)",
                Map.of(
                        "tenantId", device.tenantId(),
                        "unidadeId", device.unidadeAtendimentoId(),
                        "deviceId", device.dispositivoId(),
                        "clienteConsumoId", cliente.getId(),
                        "telefoneMascarado", masked
                ),
                clientIp,
                userAgent
        );

        return new AssistidoVerifyResult(
                cliente.getId(),
                sessao.getId(),
                masked,
                sessao.getIdentificacaoStatus(),
                sessao.isIdentificadoPorOtp(),
                sessao.getTelefoneIdentificadoEm(),
                "IDENTIFICADA"
        );
    }

    private void requireCapability(DevicePrincipal device) {
        if (device == null) throw new DeviceUnauthorizedException("DevicePrincipal ausente.");
        // mantido apenas para compatibilidade; as validações sensíveis usam DeviceCapabilityService.
    }

    private void validateSessaoForAssistido(DevicePrincipal device, SessaoConsumo sessao, String ip, String userAgent) {
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");
        if (device.unidadeAtendimentoId() != null) {
            Long sessaoUa = sessao.getUnidadeAtendimento() != null ? sessao.getUnidadeAtendimento().getId() : null;
            if (sessaoUa == null || !device.unidadeAtendimentoId().equals(sessaoUa)) {
                deviceCapabilityService.requireCrossUnitIfNeeded(device, sessaoUa, ip, userAgent);
            }
        }
    }

    public record AssistidoVerifyResult(Long clienteConsumoId,
                                        Long sessaoConsumoId,
                                        String telefoneMascarado,
                                        SessaoIdentificacaoStatus identificacaoStatus,
                                        boolean identificadoPorOtp,
                                        Instant identificadoEm,
                                        String statusMensagem) {}
}
