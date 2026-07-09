package com.restaurante.consumo.identificacao.service;

import com.restaurante.consumo.identificacao.entity.ClienteConsumo;
import com.restaurante.consumo.identificacao.entity.TelefoneOtpChallenge;
import com.restaurante.dto.request.AbrirSessaoRequest;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.OtpPurpose;
import com.restaurante.model.enums.SessaoIdentificacaoStatus;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.service.FundoConsumoService;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.service.SessaoConsumoService;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.restaurante.repository.SessaoConsumoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConsumoIdentificadoPublicService {

    private final QrCodeOperacionalService qrCodeOperacionalService;
    private final SessaoConsumoService sessaoConsumoService;
    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final FundoConsumoService fundoConsumoService;
    private final TelefoneNormalizerService phoneNormalizerService;
    private final TelefoneOtpService otpService;
    private final ClienteConsumoService clienteConsumoService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public TelefoneOtpService.OtpRequestResult requestOtpForIdentify(String qrToken,
                                                                     String rawPhone,
                                                                     OtpPurpose purpose,
                                                                     String ip,
                                                                     String userAgent) {
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(qrToken);
        SessaoConsumo sessao = resolveOrCreateSessaoForQr(qr);

        String maskedPhone = phoneNormalizerService.mask(phoneNormalizerService.normalizeOrThrow(rawPhone));
        try {
            TelefoneOtpService.OtpRequestResult result = otpService.requestOtp(qr.getTenant(), rawPhone, purpose, sessao, ip, userAgent);
            operationalEventLogService.logPublicEvent(
                    qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                    OperationalEventType.OTP_CHALLENGE_CREATED,
                    OperationalEntityType.TELEFONE_OTP_CHALLENGE,
                    result.getChallenge().getId(),
                    OperationalOrigem.QR_PUBLICO,
                    "OTP challenge criado",
                    otpMetadata(qr, maskedPhone, purpose, result.getChallenge(), result.isSmsSent()),
                    ip,
                    userAgent
            );
            return result;
        } catch (BusinessException e) {
            if ("OTP_RATE_LIMIT_EXCEEDED".equals(e.getMessage())) {
                operationalEventLogService.logPublicEvent(
                        qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                        OperationalEventType.OTP_RATE_LIMITED,
                        OperationalEntityType.TELEFONE_OTP_CHALLENGE,
                        0L,
                        OperationalOrigem.QR_PUBLICO,
                        "OTP rate limited",
                        Map.of("tenantId", qr.getTenant().getId(), "telefoneMascarado", maskedPhone, "purpose", purpose != null ? purpose.name() : null),
                        ip,
                        userAgent
                );
            }
            throw e;
        }
    }

    @Transactional
    public IdentifyVerifyResult verifyOtpAndIdentifySessao(String qrToken,
                                                          Long challengeId,
                                                          String rawPhone,
                                                          String otp,
                                                          String ip,
                                                          String userAgent) {
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(qrToken);
        String phoneNormalized = phoneNormalizerService.normalizeOrThrow(rawPhone);
        String maskedPhone = phoneNormalizerService.mask(phoneNormalized);

        TelefoneOtpChallenge c;
        try {
            c = otpService.verifyOtp(qr.getTenant().getId(), challengeId, rawPhone, otp);
        } catch (BusinessException e) {
            operationalEventLogService.logPublicEvent(
                    qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                    "OTP_CHALLENGE_EXPIRED".equals(e.getMessage()) ? OperationalEventType.OTP_CHALLENGE_EXPIRED : OperationalEventType.OTP_CHALLENGE_FAILED,
                    OperationalEntityType.TELEFONE_OTP_CHALLENGE,
                    challengeId,
                    OperationalOrigem.QR_PUBLICO,
                    "Falha ao validar OTP",
                    Map.of("tenantId", qr.getTenant().getId(), "telefoneMascarado", maskedPhone, "purpose", OtpPurpose.IDENTIFICAR_SESSAO.name()),
                    ip,
                    userAgent
            );
            throw e;
        }

        SessaoConsumo sessao = c.getSessaoConsumo();
        if (sessao == null) throw new BusinessException("SESSAO_CONSUMO_NOT_FOUND");
        if (sessao.getTenant() == null || !sessao.getTenant().getId().equals(qr.getTenant().getId())) {
            throw new ResourceNotFoundException("SessaoConsumo", "id", sessao.getId());
        }
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");
        if (sessao.getClienteConsumo() != null) throw new BusinessException("SESSAO_CONSUMO_ALREADY_IDENTIFIED");

        if (qr.getMesa() != null && (sessao.getMesa() == null || !qr.getMesa().getId().equals(sessao.getMesa().getId()))) {
            throw new ResourceNotFoundException("SessaoConsumo", "id", sessao.getId());
        }
        if (qr.getUnidadeAtendimento() != null && (sessao.getUnidadeAtendimento() == null || !qr.getUnidadeAtendimento().getId().equals(sessao.getUnidadeAtendimento().getId()))) {
            throw new ResourceNotFoundException("SessaoConsumo", "id", sessao.getId());
        }

        ClienteConsumoService.GetOrCreateResult created = clienteConsumoService.getOrCreateByPhone(qr.getTenant(), rawPhone, phoneNormalized);
        ClienteConsumo cliente = created.cliente();
        if (created.created()) {
            operationalEventLogService.logPublicEvent(
                    qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                    OperationalEventType.CLIENTE_CONSUMO_CREATED,
                    OperationalEntityType.CLIENTE_CONSUMO,
                    cliente.getId(),
                    OperationalOrigem.QR_PUBLICO,
                    "ClienteConsumo criado",
                    Map.of("tenantId", qr.getTenant().getId(), "clienteConsumoId", cliente.getId(), "telefoneMascarado", maskedPhone),
                    ip,
                    userAgent
            );
        }

        boolean wasVerified = cliente.isTelefoneVerificado();
        cliente = clienteConsumoService.markPhoneVerified(cliente);

        sessao.setClienteConsumo(cliente);
        sessao.setTelefoneIdentificado(phoneNormalized);
        sessao.setTelefoneIdentificadoEm(Instant.now());
        sessao.setIdentificacaoStatus(SessaoIdentificacaoStatus.IDENTIFICADA);
        sessao.setIdentificadoPorOtp(true);
        sessaoConsumoRepository.save(sessao);

        if (!wasVerified) {
            operationalEventLogService.logPublicEvent(
                    qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                    OperationalEventType.CLIENTE_CONSUMO_PHONE_VERIFIED,
                    OperationalEntityType.CLIENTE_CONSUMO,
                    cliente.getId(),
                    OperationalOrigem.QR_PUBLICO,
                    "Telefone verificado por OTP",
                    Map.of("tenantId", qr.getTenant().getId(), "clienteConsumoId", cliente.getId(), "telefoneMascarado", maskedPhone),
                    ip,
                    userAgent
            );
        }

        operationalEventLogService.logPublicEvent(
                qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                OperationalEventType.OTP_CHALLENGE_VERIFIED,
                OperationalEntityType.TELEFONE_OTP_CHALLENGE,
                c.getId(),
                OperationalOrigem.QR_PUBLICO,
                "OTP validado",
                Map.of("tenantId", qr.getTenant().getId(), "telefoneMascarado", maskedPhone, "purpose", c.getPurpose() != null ? c.getPurpose().name() : null),
                ip,
                userAgent
        );
        operationalEventLogService.logPublicEvent(
                qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                OperationalEventType.SESSAO_CONSUMO_IDENTIFICADA,
                OperationalEntityType.SESSAO_CONSUMO,
                sessao.getId(),
                OperationalOrigem.QR_PUBLICO,
                "Sessão identificada por telefone+OTP",
                Map.of("tenantId", qr.getTenant().getId(), "sessaoConsumoId", sessao.getId(), "clienteConsumoId", cliente.getId(), "telefoneMascarado", maskedPhone),
                ip,
                userAgent
        );

        return new IdentifyVerifyResult(cliente.getId(), sessao.getId(), maskedPhone, sessao.getIdentificacaoStatus());
    }

    @Transactional
    public TelefoneOtpService.OtpRequestResult requestOtpForRecovery(String qrToken,
                                                                     String rawPhone,
                                                                     String ip,
                                                                     String userAgent) {
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(qrToken);
        String phoneNormalized = phoneNormalizerService.normalizeOrThrow(rawPhone);
        String maskedPhone = phoneNormalizerService.mask(phoneNormalized);
        try {
            TelefoneOtpService.OtpRequestResult result = otpService.requestOtp(qr.getTenant(), rawPhone, OtpPurpose.RECUPERAR_SESSAO, null, ip, userAgent);
            operationalEventLogService.logPublicEvent(
                    qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                    OperationalEventType.OTP_CHALLENGE_CREATED,
                    OperationalEntityType.TELEFONE_OTP_CHALLENGE,
                    result.getChallenge().getId(),
                    OperationalOrigem.QR_PUBLICO,
                    "OTP recovery criado",
                    otpMetadata(qr, maskedPhone, OtpPurpose.RECUPERAR_SESSAO, result.getChallenge(), result.isSmsSent()),
                    ip,
                    userAgent
            );
            return result;
        } catch (BusinessException e) {
            if ("OTP_RATE_LIMIT_EXCEEDED".equals(e.getMessage())) {
                operationalEventLogService.logPublicEvent(
                        qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                        OperationalEventType.OTP_RATE_LIMITED,
                        OperationalEntityType.TELEFONE_OTP_CHALLENGE,
                        0L,
                        OperationalOrigem.QR_PUBLICO,
                        "OTP rate limited",
                        Map.of("tenantId", qr.getTenant().getId(), "telefoneMascarado", maskedPhone, "purpose", OtpPurpose.RECUPERAR_SESSAO.name()),
                        ip,
                        userAgent
                );
            }
            throw e;
        }
    }

    @Transactional
    public List<RecoveredSessaoResumo> verifyOtpAndRecoverSessions(String qrToken,
                                                                  Long challengeId,
                                                                  String rawPhone,
                                                                  String otp,
                                                                  String ip,
                                                                  String userAgent) {
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(qrToken);
        String phoneNormalized = phoneNormalizerService.normalizeOrThrow(rawPhone);
        String maskedPhone = phoneNormalizerService.mask(phoneNormalized);

        TelefoneOtpChallenge c;
        try {
            c = otpService.verifyOtp(qr.getTenant().getId(), challengeId, rawPhone, otp);
        } catch (BusinessException e) {
            operationalEventLogService.logPublicEvent(
                    qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                    "OTP_CHALLENGE_EXPIRED".equals(e.getMessage()) ? OperationalEventType.OTP_CHALLENGE_EXPIRED : OperationalEventType.OTP_CHALLENGE_FAILED,
                    OperationalEntityType.TELEFONE_OTP_CHALLENGE,
                    challengeId,
                    OperationalOrigem.QR_PUBLICO,
                    "Falha ao validar OTP recovery",
                    Map.of("tenantId", qr.getTenant().getId(), "telefoneMascarado", maskedPhone, "purpose", OtpPurpose.RECUPERAR_SESSAO.name()),
                    ip,
                    userAgent
            );
            throw e;
        }
        if (c.getPurpose() != OtpPurpose.RECUPERAR_SESSAO) throw new BusinessException("OTP_INVALID");

        List<SessaoConsumo> sessoes = sessaoConsumoRepository.findOpenByTenantAndTelefoneIdentificado(
                qr.getTenant().getId(),
                phoneNormalized,
                qr.getUnidadeAtendimento() != null ? qr.getUnidadeAtendimento().getId() : null,
                StatusSessaoConsumo.ABERTA
        );

        operationalEventLogService.logPublicEvent(
                qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                OperationalEventType.OTP_CHALLENGE_VERIFIED,
                OperationalEntityType.TELEFONE_OTP_CHALLENGE,
                c.getId(),
                OperationalOrigem.QR_PUBLICO,
                "OTP recovery validado",
                Map.of("tenantId", qr.getTenant().getId(), "telefoneMascarado", maskedPhone, "purpose", c.getPurpose().name()),
                ip,
                userAgent
        );
        operationalEventLogService.logPublicEvent(
                qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                OperationalEventType.SESSAO_CONSUMO_RECUPERADA_POR_TELEFONE,
                OperationalEntityType.SESSAO_CONSUMO,
                0L,
                OperationalOrigem.QR_PUBLICO,
                "Recuperação de sessão por telefone",
                Map.of("tenantId", qr.getTenant().getId(), "telefoneMascarado", maskedPhone, "totalSessoes", sessoes.size()),
                ip,
                userAgent
        );

        return sessoes.stream().limit(10).map(s -> {
            String unidade = s.getUnidadeAtendimento() != null ? s.getUnidadeAtendimento().getNome() : null;
            String saldo = null;
            try {
                saldo = fundoConsumoService.consultarSaldoPorToken(s.getQrCodeSessao()).toPlainString();
            } catch (Exception ignored) {
            }
            return new RecoveredSessaoResumo(s.getId(), s.getQrCodeSessao(), s.getAbertaEm(), unidade, saldo, s.getStatus().name());
        }).toList();
    }

    private SessaoConsumo resolveOrCreateSessaoForQr(QrCodeOperacional qr) {
        if (qr.getMesa() != null) {
            SessaoConsumo open = sessaoConsumoRepository.findByTenantIdAndMesaIdAndStatus(qr.getTenant().getId(), qr.getMesa().getId(), StatusSessaoConsumo.ABERTA)
                    .orElse(null);
            if (open != null) return open;
        }
        AbrirSessaoRequest req = AbrirSessaoRequest.builder()
                .mesaId(qr.getMesa() != null ? qr.getMesa().getId() : null)
                .unidadeAtendimentoId(qr.getUnidadeAtendimento() != null ? qr.getUnidadeAtendimento().getId() : null)
                .modoAnonimo(true)
                .tipoSessao(com.restaurante.model.enums.TipoSessao.PRE_PAGO)
                .build();
        Long sessaoId = sessaoConsumoService.abrir(req).getId();
        return sessaoConsumoRepository.findByIdAndTenantId(sessaoId, qr.getTenant().getId())
                .orElseThrow(() -> new ResourceNotFoundException("SessaoConsumo", "id", sessaoId));
    }

    private Map<String, Object> otpMetadata(QrCodeOperacional qr,
                                           String maskedPhone,
                                           OtpPurpose purpose,
                                           TelefoneOtpChallenge c,
                                           boolean smsSent) {
        Map<String, Object> m = new HashMap<>();
        m.put("tenantId", qr.getTenant().getId());
        m.put("unidadeId", qr.getUnidadeAtendimento() != null ? qr.getUnidadeAtendimento().getId() : null);
        m.put("sessaoConsumoId", c.getSessaoConsumo() != null ? c.getSessaoConsumo().getId() : null);
        m.put("telefoneMascarado", maskedPhone);
        m.put("purpose", purpose != null ? purpose.name() : null);
        m.put("expiresAt", c.getExpiresAt());
        m.put("smsSent", smsSent);
        return m;
    }

    public record IdentifyVerifyResult(Long clienteConsumoId,
                                       Long sessaoConsumoId,
                                       String telefoneMascarado,
                                       SessaoIdentificacaoStatus identificacaoStatus) {}

    public record RecoveredSessaoResumo(Long sessaoId,
                                        String codigoConsumo,
                                        java.time.LocalDateTime abertaEm,
                                        String unidade,
                                        String saldoFundo,
                                        String status) {}
}
