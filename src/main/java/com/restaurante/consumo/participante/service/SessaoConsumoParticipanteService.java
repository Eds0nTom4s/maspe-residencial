package com.restaurante.consumo.participante.service;

import com.restaurante.consumo.identificacao.entity.ClienteConsumo;
import com.restaurante.consumo.identificacao.entity.TelefoneOtpChallenge;
import com.restaurante.consumo.identificacao.service.ClienteConsumoService;
import com.restaurante.consumo.identificacao.service.TelefoneNormalizerService;
import com.restaurante.consumo.identificacao.service.TelefoneOtpService;
import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.consumo.participante.repository.SessaoConsumoParticipanteRepository;
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
import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.service.SessaoConsumoService;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SessaoConsumoParticipanteService {

    private final QrCodeOperacionalService qrCodeOperacionalService;
    private final SessaoConsumoService sessaoConsumoService;
    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final SessaoConsumoParticipanteRepository participanteRepository;
    private final TelefoneNormalizerService phoneNormalizerService;
    private final TelefoneOtpService otpService;
    private final ClienteConsumoService clienteConsumoService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public TelefoneOtpService.OtpRequestResult requestJoinByQr(String qrToken,
                                                               String rawPhone,
                                                               String nomeExibicao,
                                                               String ip,
                                                               String userAgent) {
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(qrToken);
        SessaoConsumo sessao = resolveOrCreateSessaoForQr(qr);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");

        String phoneNormalized = phoneNormalizerService.normalizeOrThrow(rawPhone);
        String maskedPhone = phoneNormalizerService.mask(phoneNormalized);

        try {
            TelefoneOtpService.OtpRequestResult result = otpService.requestOtp(qr.getTenant(), rawPhone, OtpPurpose.ENTRAR_SESSAO_COMPARTILHADA, sessao, ip, userAgent);
            Map<String, Object> meta = new HashMap<>();
            meta.put("tenantId", qr.getTenant().getId());
            meta.put("unidadeId", qr.getUnidadeAtendimento() != null ? qr.getUnidadeAtendimento().getId() : null);
            meta.put("sessaoConsumoId", sessao.getId());
            meta.put("telefoneMascarado", maskedPhone);
            meta.put("purpose", OtpPurpose.ENTRAR_SESSAO_COMPARTILHADA.name());
            meta.put("challengeId", result.getChallenge().getId());
            meta.put("smsSent", result.isSmsSent());
            operationalEventLogService.logPublicEvent(
                    qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                    OperationalEventType.SESSAO_PARTICIPANTE_JOIN_REQUESTED,
                    OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                    0L,
                    OperationalOrigem.QR_PUBLICO,
                    "Join requested (OTP criado)",
                    meta,
                    ip, userAgent
            );
            return result;
        } catch (BusinessException e) {
            throw e;
        }
    }

    @Transactional
    public VerifyJoinResult verifyJoinOtp(String qrToken,
                                         Long challengeId,
                                         String rawPhone,
                                         String otp,
                                         String nomeExibicao,
                                         String ip,
                                         String userAgent) {
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(qrToken);
        SessaoConsumo sessao = resolveOrCreateSessaoForQr(qr);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");

        String phoneNormalized = phoneNormalizerService.normalizeOrThrow(rawPhone);
        String maskedPhone = phoneNormalizerService.mask(phoneNormalized);

        TelefoneOtpChallenge c;
        try {
            c = otpService.validateOtpPendingOrThrow(qr.getTenant().getId(), challengeId, rawPhone, otp);
        } catch (BusinessException e) {
            operationalEventLogService.logPublicEvent(
                    qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                    "OTP_CHALLENGE_EXPIRED".equals(e.getMessage()) ? OperationalEventType.OTP_CHALLENGE_EXPIRED : OperationalEventType.OTP_CHALLENGE_FAILED,
                    OperationalEntityType.TELEFONE_OTP_CHALLENGE,
                    challengeId,
                    OperationalOrigem.QR_PUBLICO,
                    "Falha ao validar OTP join",
                    Map.of("tenantId", qr.getTenant().getId(), "telefoneMascarado", maskedPhone, "purpose", OtpPurpose.ENTRAR_SESSAO_COMPARTILHADA.name()),
                    ip,
                    userAgent
            );
            throw e;
        }

        if (c.getPurpose() != OtpPurpose.ENTRAR_SESSAO_COMPARTILHADA) throw new BusinessException("OTP_CHALLENGE_PURPOSE_INVALID");
        if (c.getSessaoConsumo() == null || !c.getSessaoConsumo().getId().equals(sessao.getId())) throw new BusinessException("OTP_CHALLENGE_SESSION_MISMATCH");

        ClienteConsumoService.GetOrCreateResult gc = clienteConsumoService.getOrCreateByPhone(qr.getTenant(), rawPhone, phoneNormalized);
        ClienteConsumo cliente = gc.cliente();
        if (cliente.getStatus() == com.restaurante.model.enums.ClienteConsumoStatus.BLOCKED) throw new BusinessException("CLIENTE_CONSUMO_BLOCKED");
        cliente = clienteConsumoService.markPhoneVerified(cliente);

        ensureOwnerIfSessaoHasPrincipal(qr.getTenant().getId(), sessao, ip, userAgent);

        SessaoConsumoParticipante participante = participanteRepository.findForUpdateBySessaoAndCliente(qr.getTenant().getId(), sessao.getId(), cliente.getId())
                .orElse(null);

        Instant now = Instant.now();
        if (participante != null && participante.getStatus() == SessaoParticipanteStatus.ACTIVE) {
            otpService.consumeChallenge(qr.getTenant().getId(), challengeId);
            return new VerifyJoinResult(participante.getId(), cliente.getId(), sessao.getId(), participante.getRole(), participante.getStatus(), maskedPhone, participante.getJoinedAt());
        }

        SessaoParticipanteRole role = decideRoleForJoin(sessao, cliente);
        if (participante == null) {
            participante = new SessaoConsumoParticipante();
            participante.setTenant(qr.getTenant());
            participante.setSessaoConsumo(sessao);
            participante.setClienteConsumo(cliente);
            participante.setTelefoneNormalizado(phoneNormalized);
            participante.setRole(role);
            participante.setStatus(SessaoParticipanteStatus.ACTIVE);
            participante.setNomeExibicao(nomeExibicao);
            participante.setJoinedAt(now);
            participante.setLastActivityAt(now);
        } else {
            participante.setRole(role);
            participante.setStatus(SessaoParticipanteStatus.ACTIVE);
            if (participante.getJoinedAt() == null) participante.setJoinedAt(now);
            participante.setLastActivityAt(now);
            if (nomeExibicao != null && !nomeExibicao.isBlank()) participante.setNomeExibicao(nomeExibicao);
        }
        participante = participanteRepository.save(participante);

        // se a sessão não tinha cliente principal, o primeiro participante vira owner e também identifica a sessão (sem mudar pagamento)
        if (sessao.getClienteConsumo() == null && role == SessaoParticipanteRole.OWNER) {
            sessao.setClienteConsumo(cliente);
            sessao.setTelefoneIdentificado(cliente.getTelefone());
            sessao.setTelefoneIdentificadoEm(now);
            sessao.setIdentificacaoStatus(SessaoIdentificacaoStatus.IDENTIFICADA);
            sessao.setIdentificadoPorOtp(true);
            sessaoConsumoRepository.save(sessao);
        }

        otpService.consumeChallenge(qr.getTenant().getId(), challengeId);

        Map<String, Object> verifiedMeta = new HashMap<>();
        verifiedMeta.put("tenantId", qr.getTenant().getId());
        verifiedMeta.put("unidadeId", qr.getUnidadeAtendimento() != null ? qr.getUnidadeAtendimento().getId() : null);
        verifiedMeta.put("sessaoConsumoId", sessao.getId());
        verifiedMeta.put("participanteId", participante.getId());
        verifiedMeta.put("clienteConsumoId", cliente.getId());
        verifiedMeta.put("role", role.name());
        verifiedMeta.put("status", participante.getStatus().name());
        verifiedMeta.put("telefoneMascarado", maskedPhone);
        operationalEventLogService.logPublicEvent(
                qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                OperationalEventType.SESSAO_PARTICIPANTE_JOIN_VERIFIED,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                participante.getId(),
                OperationalOrigem.QR_PUBLICO,
                "Participante entrou na sessão",
                verifiedMeta,
                ip, userAgent
        );

        return new VerifyJoinResult(participante.getId(), cliente.getId(), sessao.getId(), role, participante.getStatus(), maskedPhone, participante.getJoinedAt());
    }

    @Transactional(readOnly = true)
    public List<SessaoParticipanteResumo> listByQr(String qrToken) {
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(qrToken);
        SessaoConsumo sessao = resolveOrCreateSessaoForQr(qr);
        List<SessaoConsumoParticipante> list = participanteRepository.listBySessao(qr.getTenant().getId(), sessao.getId());
        return list.stream()
                .filter(p -> p.getStatus() == SessaoParticipanteStatus.ACTIVE)
                .map(p -> new SessaoParticipanteResumo(p.getId(), p.getNomeExibicao(), p.getRole(), p.getStatus(), p.getJoinedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SessaoConsumoParticipante requireActiveParticipant(Long tenantId, Long sessaoId, Long participanteId) {
        SessaoConsumoParticipante p = participanteRepository.findByTenant_IdAndId(tenantId, participanteId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSAO_PARTICIPANTE_NOT_FOUND"));
        if (!p.getSessaoConsumo().getId().equals(sessaoId)) throw new BusinessException("SESSAO_PARTICIPANTE_WRONG_SESSION");
        if (p.getStatus() != SessaoParticipanteStatus.ACTIVE) throw new BusinessException("SESSAO_PARTICIPANTE_NOT_ACTIVE");
        return p;
    }

    private SessaoParticipanteRole decideRoleForJoin(SessaoConsumo sessao, ClienteConsumo joining) {
        if (sessao.getClienteConsumo() != null) {
            if (sessao.getClienteConsumo().getId().equals(joining.getId())) return SessaoParticipanteRole.OWNER;
            return SessaoParticipanteRole.MEMBER;
        }
        // sem cliente principal: primeiro participante vira OWNER
        long activeCount = participanteRepository.listBySessaoAndStatus(sessao.getTenant().getId(), sessao.getId(), SessaoParticipanteStatus.ACTIVE).size();
        return activeCount == 0 ? SessaoParticipanteRole.OWNER : SessaoParticipanteRole.MEMBER;
    }

    private void ensureOwnerIfSessaoHasPrincipal(Long tenantId, SessaoConsumo sessao, String ip, String userAgent) {
        if (sessao.getClienteConsumo() == null) return;
        ClienteConsumo principal = sessao.getClienteConsumo();
        SessaoConsumoParticipante existing = participanteRepository.findByTenant_IdAndSessaoConsumo_IdAndClienteConsumo_Id(tenantId, sessao.getId(), principal.getId())
                .orElse(null);
        if (existing != null) return;
        SessaoConsumoParticipante owner = new SessaoConsumoParticipante();
        owner.setTenant(sessao.getTenant());
        owner.setSessaoConsumo(sessao);
        owner.setClienteConsumo(principal);
        owner.setTelefoneNormalizado(principal.getTelefoneNormalizado());
        owner.setRole(SessaoParticipanteRole.OWNER);
        owner.setStatus(SessaoParticipanteStatus.ACTIVE);
        owner.setJoinedAt(Instant.now());
        owner.setLastActivityAt(Instant.now());
        participanteRepository.save(owner);

        operationalEventLogService.logPublicEvent(
                sessao.getTenant(), sessao.getInstituicao(), sessao.getUnidadeAtendimento(), sessao.getMesa(), null,
                OperationalEventType.SESSAO_PARTICIPANTE_ADDED,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                owner.getId(),
                OperationalOrigem.SYSTEM,
                "Owner participante bootstrap",
                Map.of("tenantId", tenantId, "sessaoConsumoId", sessao.getId(), "participanteId", owner.getId(), "clienteConsumoId", principal.getId(), "role", "OWNER"),
                ip, userAgent
        );
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

    public record VerifyJoinResult(Long participanteId,
                                   Long clienteConsumoId,
                                   Long sessaoConsumoId,
                                   SessaoParticipanteRole role,
                                   SessaoParticipanteStatus status,
                                   String telefoneMascarado,
                                   Instant joinedAt) {}

    public record SessaoParticipanteResumo(Long participanteId,
                                          String nomeExibicao,
                                          SessaoParticipanteRole role,
                                          SessaoParticipanteStatus status,
                                          Instant joinedAt) {}
}
