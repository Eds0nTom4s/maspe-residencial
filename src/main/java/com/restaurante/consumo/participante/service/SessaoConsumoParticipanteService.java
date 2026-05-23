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
import com.restaurante.model.enums.SessaoParticipantEntryPolicy;
import com.restaurante.model.enums.SessaoIdentificacaoStatus;
import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.exception.DeviceForbiddenException;
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
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
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

        SessaoParticipantEntryPolicy policy = effectivePolicy(sessao);
        if (policy == SessaoParticipantEntryPolicy.INVITE_ONLY) throw new BusinessException("SESSION_INVITE_ONLY");
        if (policy == SessaoParticipantEntryPolicy.POS_ONLY) throw new BusinessException("SESSION_POS_ONLY");

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
            meta.put("entryPolicy", policy.name());
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

        SessaoParticipantEntryPolicy policy = effectivePolicy(sessao);
        if (policy == SessaoParticipantEntryPolicy.POS_ONLY) throw new BusinessException("SESSION_POS_ONLY");

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
            if (policy == SessaoParticipantEntryPolicy.INVITE_ONLY) throw new BusinessException("PARTICIPANT_INVITE_NOT_FOUND");
            participante = new SessaoConsumoParticipante();
            participante.setTenant(qr.getTenant());
            participante.setSessaoConsumo(sessao);
            participante.setClienteConsumo(cliente);
            participante.setTelefoneNormalizado(phoneNormalized);
            participante.setRole(role);
            applyStatusByPolicyOnVerify(participante, policy, now);
            participante.setNomeExibicao(nomeExibicao);
            if (participante.getJoinedAt() == null) participante.setJoinedAt(now);
            participante.setLastActivityAt(now);
        } else {
            if (participante.getStatus() == SessaoParticipanteStatus.BLOCKED) throw new BusinessException("SESSAO_PARTICIPANTE_NOT_ACTIVE");
            if (policy == SessaoParticipantEntryPolicy.INVITE_ONLY && participante.getStatus() != SessaoParticipanteStatus.INVITED) {
                throw new BusinessException("PARTICIPANT_INVITE_NOT_FOUND");
            }
            if (policy != SessaoParticipantEntryPolicy.INVITE_ONLY) {
                participante.setRole(role);
            }
            applyStatusByPolicyOnVerify(participante, policy, now);
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

        if (policy == SessaoParticipantEntryPolicy.OTP_REQUIRES_OWNER_APPROVAL && participante.getStatus() == SessaoParticipanteStatus.PENDING_APPROVAL) {
            operationalEventLogService.logPublicEvent(
                    qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                    OperationalEventType.SESSAO_PARTICIPANTE_JOIN_PENDING_APPROVAL,
                    OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                    participante.getId(),
                    OperationalOrigem.QR_PUBLICO,
                    "Participante pendente de aprovação",
                    Map.of(
                            "tenantId", qr.getTenant().getId(),
                            "sessaoConsumoId", sessao.getId(),
                            "participanteId", participante.getId(),
                            "clienteConsumoId", cliente.getId(),
                            "entryPolicy", policy.name(),
                            "telefoneMascarado", maskedPhone
                    ),
                    ip,
                    userAgent
            );
        }

        Map<String, Object> verifiedMeta = new HashMap<>();
        verifiedMeta.put("tenantId", qr.getTenant().getId());
        verifiedMeta.put("unidadeId", qr.getUnidadeAtendimento() != null ? qr.getUnidadeAtendimento().getId() : null);
        verifiedMeta.put("sessaoConsumoId", sessao.getId());
        verifiedMeta.put("participanteId", participante.getId());
        verifiedMeta.put("clienteConsumoId", cliente.getId());
        verifiedMeta.put("role", role.name());
        verifiedMeta.put("status", participante.getStatus().name());
        verifiedMeta.put("telefoneMascarado", maskedPhone);
        verifiedMeta.put("entryPolicy", policy.name());
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

    @Transactional
    public TelefoneOtpService.OtpRequestResult requestOwnerAuthOtp(String qrToken, String rawPhone, String ip, String userAgent) {
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(qrToken);
        SessaoConsumo sessao = resolveOrCreateSessaoForQr(qr);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");

        String phoneNormalized = phoneNormalizerService.normalizeOrThrow(rawPhone);
        String maskedPhone = phoneNormalizerService.mask(phoneNormalized);

        SessaoConsumoParticipante owner = participanteRepository
                .findByTenant_IdAndSessaoConsumo_IdAndTelefoneNormalizadoAndRoleAndStatus(
                        qr.getTenant().getId(), sessao.getId(), phoneNormalized, SessaoParticipanteRole.OWNER, SessaoParticipanteStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException("OWNER_NOT_ACTIVE"));

        TelefoneOtpService.OtpRequestResult result = otpService.requestOtp(qr.getTenant(), rawPhone, OtpPurpose.OWNER_APPROVAL_AUTH, sessao, ip, userAgent);

        operationalEventLogService.logPublicEvent(
                qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                OperationalEventType.SESSAO_OWNER_AUTH_OTP_REQUESTED,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                owner.getId(),
                OperationalOrigem.QR_PUBLICO,
                "Owner auth OTP solicitado",
                Map.of(
                        "tenantId", qr.getTenant().getId(),
                        "sessaoConsumoId", sessao.getId(),
                        "ownerParticipanteId", owner.getId(),
                        "challengeId", result.getChallenge().getId(),
                        "telefoneMascarado", maskedPhone,
                        "purpose", OtpPurpose.OWNER_APPROVAL_AUTH.name()
                ),
                ip,
                userAgent
        );

        return result;
    }

    @Transactional
    public SessaoConsumoParticipante assertOwnerByOtp(String qrToken, Long ownerChallengeId, String rawPhone, String otp, String ip, String userAgent) {
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(qrToken);
        SessaoConsumo sessao = resolveOrCreateSessaoForQr(qr);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");

        String phoneNormalized = phoneNormalizerService.normalizeOrThrow(rawPhone);
        TelefoneOtpChallenge ch = otpService.validateOtpPendingOrThrow(qr.getTenant().getId(), ownerChallengeId, rawPhone, otp);
        if (ch.getPurpose() != OtpPurpose.OWNER_APPROVAL_AUTH) throw new BusinessException("OWNER_AUTH_INVALID");
        if (ch.getSessaoConsumo() == null || !ch.getSessaoConsumo().getId().equals(sessao.getId())) throw new BusinessException("OTP_CHALLENGE_SESSION_MISMATCH");

        SessaoConsumoParticipante owner = participanteRepository
                .findByTenant_IdAndSessaoConsumo_IdAndTelefoneNormalizadoAndRoleAndStatus(
                        qr.getTenant().getId(), sessao.getId(), phoneNormalized, SessaoParticipanteRole.OWNER, SessaoParticipanteStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException("OWNER_NOT_ACTIVE"));

        otpService.consumeChallenge(qr.getTenant().getId(), ownerChallengeId);

        operationalEventLogService.logPublicEvent(
                qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                OperationalEventType.SESSAO_OWNER_AUTH_VERIFIED,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                owner.getId(),
                OperationalOrigem.QR_PUBLICO,
                "Owner auth verificado",
                Map.of(
                        "tenantId", qr.getTenant().getId(),
                        "sessaoConsumoId", sessao.getId(),
                        "ownerParticipanteId", owner.getId()
                ),
                ip,
                userAgent
        );
        return owner;
    }

    @Transactional
    public List<SessaoConsumoParticipante> listPendingForOwner(String qrToken, Long ownerChallengeId, String ownerPhone, String otp, String ip, String userAgent) {
        SessaoConsumoParticipante owner = assertOwnerByOtp(qrToken, ownerChallengeId, ownerPhone, otp, ip, userAgent);
        SessaoConsumo sessao = owner.getSessaoConsumo();
        return participanteRepository.listBySessaoAndStatus(owner.getTenant().getId(), sessao.getId(), SessaoParticipanteStatus.PENDING_APPROVAL);
    }

    @Transactional
    public SessaoConsumoParticipante approveByOwner(String qrToken,
                                                    Long participanteId,
                                                    Long ownerChallengeId,
                                                    String ownerPhone,
                                                    String otp,
                                                    String reason,
                                                    String ip,
                                                    String userAgent) {
        SessaoConsumoParticipante owner = assertOwnerByOtp(qrToken, ownerChallengeId, ownerPhone, otp, ip, userAgent);
        SessaoConsumo sessao = owner.getSessaoConsumo();

        SessaoConsumoParticipante p = participanteRepository.findForUpdateById(owner.getTenant().getId(), participanteId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSAO_PARTICIPANTE_NOT_FOUND"));
        if (!p.getSessaoConsumo().getId().equals(sessao.getId())) throw new BusinessException("SESSAO_PARTICIPANTE_WRONG_SESSION");
        if (p.getStatus() != SessaoParticipanteStatus.PENDING_APPROVAL) throw new BusinessException("PARTICIPANT_NOT_PENDING_APPROVAL");

        p.setStatus(SessaoParticipanteStatus.ACTIVE);
        if (p.getJoinedAt() == null) p.setJoinedAt(Instant.now());
        p.setLastActivityAt(Instant.now());
        p.setApprovedByParticipanteId(owner.getId());
        p.setApprovalDecidedAt(Instant.now());
        p.setApprovalReason(sanitizeReason(reason));
        SessaoConsumoParticipante saved = participanteRepository.save(p);

        operationalEventLogService.logPublicEvent(
                owner.getTenant(), sessao.getInstituicao(), sessao.getUnidadeAtendimento(), sessao.getMesa(), null,
                OperationalEventType.SESSAO_PARTICIPANTE_APPROVED_BY_OWNER,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                saved.getId(),
                OperationalOrigem.QR_PUBLICO,
                "Participante aprovado pelo OWNER",
                Map.of(
                        "tenantId", owner.getTenant().getId(),
                        "sessaoConsumoId", sessao.getId(),
                        "participanteId", saved.getId(),
                        "ownerParticipanteId", owner.getId(),
                        "oldStatus", SessaoParticipanteStatus.PENDING_APPROVAL.name(),
                        "newStatus", saved.getStatus().name()
                ),
                ip,
                userAgent
        );
        return saved;
    }

    @Transactional
    public SessaoConsumoParticipante rejectByOwner(String qrToken,
                                                   Long participanteId,
                                                   Long ownerChallengeId,
                                                   String ownerPhone,
                                                   String otp,
                                                   String reason,
                                                   String ip,
                                                   String userAgent) {
        SessaoConsumoParticipante owner = assertOwnerByOtp(qrToken, ownerChallengeId, ownerPhone, otp, ip, userAgent);
        SessaoConsumo sessao = owner.getSessaoConsumo();

        SessaoConsumoParticipante p = participanteRepository.findForUpdateById(owner.getTenant().getId(), participanteId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSAO_PARTICIPANTE_NOT_FOUND"));
        if (!p.getSessaoConsumo().getId().equals(sessao.getId())) throw new BusinessException("SESSAO_PARTICIPANTE_WRONG_SESSION");
        if (p.getStatus() != SessaoParticipanteStatus.PENDING_APPROVAL) throw new BusinessException("PARTICIPANT_NOT_PENDING_APPROVAL");

        p.setStatus(SessaoParticipanteStatus.REJECTED);
        p.setRejectedByParticipanteId(owner.getId());
        p.setApprovalDecidedAt(Instant.now());
        p.setRejectionReason(sanitizeReason(reason));
        SessaoConsumoParticipante saved = participanteRepository.save(p);

        operationalEventLogService.logPublicEvent(
                owner.getTenant(), sessao.getInstituicao(), sessao.getUnidadeAtendimento(), sessao.getMesa(), null,
                OperationalEventType.SESSAO_PARTICIPANTE_REJECTED_BY_OWNER,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                saved.getId(),
                OperationalOrigem.QR_PUBLICO,
                "Participante rejeitado pelo OWNER",
                Map.of(
                        "tenantId", owner.getTenant().getId(),
                        "sessaoConsumoId", sessao.getId(),
                        "participanteId", saved.getId(),
                        "ownerParticipanteId", owner.getId(),
                        "oldStatus", SessaoParticipanteStatus.PENDING_APPROVAL.name(),
                        "newStatus", saved.getStatus().name()
                ),
                ip,
                userAgent
        );
        return saved;
    }

    @Transactional
    public TelefoneOtpService.OtpRequestResult inviteByOwner(String qrToken,
                                                             Long ownerChallengeId,
                                                             String ownerPhone,
                                                             String ownerOtp,
                                                             String invitedRawPhone,
                                                             String invitedNomeExibicao,
                                                             String ip,
                                                             String userAgent) {
        SessaoConsumoParticipante owner = assertOwnerByOtp(qrToken, ownerChallengeId, ownerPhone, ownerOtp, ip, userAgent);
        SessaoConsumo sessao = owner.getSessaoConsumo();
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");

        String invitedNormalized = phoneNormalizerService.normalizeOrThrow(invitedRawPhone);
        String invitedMasked = phoneNormalizerService.mask(invitedNormalized);

        ClienteConsumoService.GetOrCreateResult gc = clienteConsumoService.getOrCreateByPhone(owner.getTenant(), invitedRawPhone, invitedNormalized);
        ClienteConsumo invitedCliente = gc.cliente();
        if (invitedCliente.getStatus() == com.restaurante.model.enums.ClienteConsumoStatus.BLOCKED) throw new BusinessException("CLIENTE_CONSUMO_BLOCKED");

        SessaoConsumoParticipante existing = participanteRepository.findForUpdateBySessaoAndCliente(owner.getTenant().getId(), sessao.getId(), invitedCliente.getId())
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == SessaoParticipanteStatus.ACTIVE) throw new BusinessException("PARTICIPANT_ALREADY_ACTIVE");
            if (existing.getStatus() == SessaoParticipanteStatus.PENDING_APPROVAL) throw new BusinessException("PARTICIPANT_ALREADY_PENDING_APPROVAL");
            if (existing.getStatus() == SessaoParticipanteStatus.INVITED) throw new BusinessException("PARTICIPANT_ALREADY_INVITED");
        }

        TelefoneOtpService.OtpRequestResult otpResult = otpService.requestOtp(owner.getTenant(), invitedRawPhone, OtpPurpose.ACEITAR_CONVITE_SESSAO, sessao, ip, userAgent);
        Instant now = Instant.now();

        SessaoConsumoParticipante p = existing != null ? existing : new SessaoConsumoParticipante();
        if (p.getId() == null) {
            p.setTenant(owner.getTenant());
            p.setSessaoConsumo(sessao);
            p.setClienteConsumo(invitedCliente);
            p.setTelefoneNormalizado(invitedNormalized);
            p.setRole(SessaoParticipanteRole.MEMBER);
        }
        p.setStatus(SessaoParticipanteStatus.INVITED);
        p.setNomeExibicao(invitedNomeExibicao != null ? invitedNomeExibicao : p.getNomeExibicao());
        p.setInvitedByParticipanteId(owner.getId());
        p.setInvitedAt(now);
        p.setInvitationExpiresAt(otpResult.getChallenge().getExpiresAt());
        p.setEntryPolicySnapshot(effectivePolicy(sessao).name());
        participanteRepository.save(p);

        operationalEventLogService.logPublicEvent(
                owner.getTenant(), sessao.getInstituicao(), sessao.getUnidadeAtendimento(), sessao.getMesa(), null,
                OperationalEventType.SESSAO_PARTICIPANTE_INVITED_BY_OWNER,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                p.getId() != null ? p.getId() : 0L,
                OperationalOrigem.QR_PUBLICO,
                "Participante convidado pelo OWNER",
                Map.of(
                        "tenantId", owner.getTenant().getId(),
                        "sessaoConsumoId", sessao.getId(),
                        "ownerParticipanteId", owner.getId(),
                        "telefoneMascarado", invitedMasked,
                        "challengeId", otpResult.getChallenge().getId(),
                        "purpose", OtpPurpose.ACEITAR_CONVITE_SESSAO.name()
                ),
                ip,
                userAgent
        );

        return otpResult;
    }

    @Transactional
    public VerifyJoinResult acceptInvite(String qrToken,
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

        TelefoneOtpChallenge c = otpService.validateOtpPendingOrThrow(qr.getTenant().getId(), challengeId, rawPhone, otp);
        if (c.getPurpose() != OtpPurpose.ACEITAR_CONVITE_SESSAO) throw new BusinessException("OTP_CHALLENGE_PURPOSE_INVALID");
        if (c.getSessaoConsumo() == null || !c.getSessaoConsumo().getId().equals(sessao.getId())) throw new BusinessException("OTP_CHALLENGE_SESSION_MISMATCH");

        ClienteConsumoService.GetOrCreateResult gc = clienteConsumoService.getOrCreateByPhone(qr.getTenant(), rawPhone, phoneNormalized);
        ClienteConsumo cliente = gc.cliente();
        if (cliente.getStatus() == com.restaurante.model.enums.ClienteConsumoStatus.BLOCKED) throw new BusinessException("CLIENTE_CONSUMO_BLOCKED");
        cliente = clienteConsumoService.markPhoneVerified(cliente);

        SessaoConsumoParticipante participante = participanteRepository.findForUpdateBySessaoAndCliente(qr.getTenant().getId(), sessao.getId(), cliente.getId())
                .orElseThrow(() -> new BusinessException("PARTICIPANT_INVITE_NOT_FOUND"));
        if (participante.getStatus() != SessaoParticipanteStatus.INVITED) throw new BusinessException("PARTICIPANT_INVITE_NOT_FOUND");
        if (participante.getInvitationExpiresAt() != null && participante.getInvitationExpiresAt().isBefore(Instant.now())) {
            participante.setStatus(SessaoParticipanteStatus.EXPIRED);
            participanteRepository.save(participante);
            throw new BusinessException("PARTICIPANT_INVITE_EXPIRED");
        }

        Instant now = Instant.now();
        participante.setStatus(SessaoParticipanteStatus.ACTIVE);
        participante.setNomeExibicao(nomeExibicao != null ? nomeExibicao : participante.getNomeExibicao());
        if (participante.getJoinedAt() == null) participante.setJoinedAt(now);
        participante.setLastActivityAt(now);
        participanteRepository.save(participante);

        otpService.consumeChallenge(qr.getTenant().getId(), challengeId);

        operationalEventLogService.logPublicEvent(
                qr.getTenant(), qr.getInstituicao(), qr.getUnidadeAtendimento(), qr.getMesa(), null,
                OperationalEventType.SESSAO_PARTICIPANTE_INVITE_ACCEPTED,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                participante.getId(),
                OperationalOrigem.QR_PUBLICO,
                "Convite aceito (participante ACTIVE)",
                Map.of(
                        "tenantId", qr.getTenant().getId(),
                        "sessaoConsumoId", sessao.getId(),
                        "participanteId", participante.getId(),
                        "clienteConsumoId", cliente.getId(),
                        "telefoneMascarado", maskedPhone
                ),
                ip,
                userAgent
        );

        return new VerifyJoinResult(participante.getId(), cliente.getId(), sessao.getId(), participante.getRole(), participante.getStatus(), maskedPhone, participante.getJoinedAt());
    }

    @Transactional(readOnly = true)
    public List<SessaoConsumoParticipante> listByDevice(DevicePrincipal device, Long sessaoId, String ip, String userAgent) {
        requireDeviceCapability(device, DeviceCapability.VIEW_SESSION_PARTICIPANTS, sessaoId, null, "List participants", ip, userAgent);
        SessaoConsumo sessao = requireSessaoForDevice(device, sessaoId);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");
        ensureOwnerIfSessaoHasPrincipal(device.tenantId(), sessao, ip, userAgent);
        return participanteRepository.listBySessao(device.tenantId(), sessaoId);
    }

    @Transactional(readOnly = true)
    public List<SessaoConsumoParticipante> listPendingByDevice(DevicePrincipal device, Long sessaoId, String ip, String userAgent) {
        requireDeviceCapability(device, DeviceCapability.VIEW_PENDING_SESSION_PARTICIPANTS, sessaoId, null, "List pending participants", ip, userAgent);
        SessaoConsumo sessao = requireSessaoForDevice(device, sessaoId);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");
        return participanteRepository.listBySessaoAndStatus(device.tenantId(), sessaoId, SessaoParticipanteStatus.PENDING_APPROVAL);
    }

    @Transactional
    public SessaoConsumoParticipante approveByDevice(DevicePrincipal device, Long sessaoId, Long participanteId, String reason, String ip, String userAgent) {
        requireDeviceCapability(device, DeviceCapability.APPROVE_SESSION_PARTICIPANT, sessaoId, participanteId, "Approve participant", ip, userAgent);
        SessaoConsumo sessao = requireSessaoForDevice(device, sessaoId);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");

        SessaoConsumoParticipante p = participanteRepository.findForUpdateById(device.tenantId(), participanteId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSAO_PARTICIPANTE_NOT_FOUND"));
        if (!p.getSessaoConsumo().getId().equals(sessaoId)) throw new BusinessException("SESSAO_PARTICIPANTE_WRONG_SESSION");
        if (p.getStatus() != SessaoParticipanteStatus.PENDING_APPROVAL) throw new BusinessException("PARTICIPANT_NOT_PENDING_APPROVAL");

        p.setStatus(SessaoParticipanteStatus.ACTIVE);
        if (p.getJoinedAt() == null) p.setJoinedAt(Instant.now());
        p.setLastActivityAt(Instant.now());
        p.setApprovedByDeviceId(device.dispositivoId());
        p.setApprovalDecidedAt(Instant.now());
        p.setApprovalReason(sanitizeReason(reason));
        SessaoConsumoParticipante saved = participanteRepository.save(p);

        operationalEventLogService.logGeneric(
                OperationalEventType.SESSAO_PARTICIPANTE_APPROVED_BY_POS,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                saved.getId(),
                OperationalOrigem.DEVICE_POS,
                "Participante aprovado por POS",
                Map.of(
                        "tenantId", device.tenantId(),
                        "unidadeId", device.unidadeAtendimentoId(),
                        "deviceId", device.dispositivoId(),
                        "sessaoConsumoId", sessaoId,
                        "participanteId", saved.getId(),
                        "oldStatus", SessaoParticipanteStatus.PENDING_APPROVAL.name(),
                        "newStatus", saved.getStatus().name()
                ),
                ip,
                userAgent
        );
        return saved;
    }

    @Transactional
    public SessaoConsumoParticipante rejectByDevice(DevicePrincipal device, Long sessaoId, Long participanteId, String reason, String ip, String userAgent) {
        requireDeviceCapability(device, DeviceCapability.REJECT_SESSION_PARTICIPANT, sessaoId, participanteId, "Reject participant", ip, userAgent);
        SessaoConsumo sessao = requireSessaoForDevice(device, sessaoId);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");

        SessaoConsumoParticipante p = participanteRepository.findForUpdateById(device.tenantId(), participanteId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSAO_PARTICIPANTE_NOT_FOUND"));
        if (!p.getSessaoConsumo().getId().equals(sessaoId)) throw new BusinessException("SESSAO_PARTICIPANTE_WRONG_SESSION");
        if (p.getStatus() != SessaoParticipanteStatus.PENDING_APPROVAL) throw new BusinessException("PARTICIPANT_NOT_PENDING_APPROVAL");

        p.setStatus(SessaoParticipanteStatus.REJECTED);
        p.setRejectedByDeviceId(device.dispositivoId());
        p.setApprovalDecidedAt(Instant.now());
        p.setRejectionReason(sanitizeReason(reason));
        SessaoConsumoParticipante saved = participanteRepository.save(p);

        operationalEventLogService.logGeneric(
                OperationalEventType.SESSAO_PARTICIPANTE_REJECTED_BY_POS,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                saved.getId(),
                OperationalOrigem.DEVICE_POS,
                "Participante rejeitado por POS",
                Map.of(
                        "tenantId", device.tenantId(),
                        "unidadeId", device.unidadeAtendimentoId(),
                        "deviceId", device.dispositivoId(),
                        "sessaoConsumoId", sessaoId,
                        "participanteId", saved.getId(),
                        "oldStatus", SessaoParticipanteStatus.PENDING_APPROVAL.name(),
                        "newStatus", saved.getStatus().name()
                ),
                ip,
                userAgent
        );
        return saved;
    }

    @Transactional
    public SessaoConsumo changeEntryPolicyByDevice(DevicePrincipal device, Long sessaoId, SessaoParticipantEntryPolicy policy, String ip, String userAgent) {
        requireDeviceCapability(device, DeviceCapability.CHANGE_SESSION_ENTRY_POLICY, sessaoId, null, "Change entry policy", ip, userAgent);
        SessaoConsumo sessao = sessaoConsumoRepository.findByIdAndTenantIdForUpdate(sessaoId, device.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("SessaoConsumo", "id", sessaoId));
        Long sessaoUnidade = sessao.getUnidadeAtendimento() != null ? sessao.getUnidadeAtendimento().getId() : null;
        if (sessaoUnidade != null && device.unidadeAtendimentoId() != null && !sessaoUnidade.equals(device.unidadeAtendimentoId())) {
            throw new ResourceNotFoundException("SessaoConsumo", "id", sessaoId);
        }
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");
        if (policy == null) throw new BusinessException("SESSION_ENTRY_POLICY_INVALID");

        SessaoParticipantEntryPolicy old = effectivePolicy(sessao);
        sessao.setParticipantEntryPolicy(policy);
        sessao.setParticipantPolicyUpdatedAt(Instant.now());
        sessao.setParticipantPolicyUpdatedByDeviceId(device.dispositivoId());
        sessaoConsumoRepository.save(sessao);

        operationalEventLogService.logGeneric(
                OperationalEventType.SESSAO_ENTRY_POLICY_CHANGED,
                OperationalEntityType.SESSAO_CONSUMO,
                sessaoId,
                OperationalOrigem.DEVICE_POS,
                "Sessão entry policy alterada por POS",
                Map.of(
                        "tenantId", device.tenantId(),
                        "unidadeId", device.unidadeAtendimentoId(),
                        "deviceId", device.dispositivoId(),
                        "sessaoConsumoId", sessaoId,
                        "oldPolicy", old.name(),
                        "newPolicy", policy.name()
                ),
                ip,
                userAgent
        );
        return sessao;
    }

    @Transactional
    public TelefoneOtpService.OtpRequestResult inviteByDevice(DevicePrincipal device,
                                                              Long sessaoId,
                                                              String invitedRawPhone,
                                                              String invitedNomeExibicao,
                                                              String ip,
                                                              String userAgent) {
        requireDeviceCapability(device, DeviceCapability.INVITE_SESSION_PARTICIPANT, sessaoId, null, "Invite participant", ip, userAgent);
        SessaoConsumo sessao = requireSessaoForDevice(device, sessaoId);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");

        String invitedNormalized = phoneNormalizerService.normalizeOrThrow(invitedRawPhone);
        String invitedMasked = phoneNormalizerService.mask(invitedNormalized);

        ClienteConsumoService.GetOrCreateResult gc = clienteConsumoService.getOrCreateByPhone(sessao.getTenant(), invitedRawPhone, invitedNormalized);
        ClienteConsumo invitedCliente = gc.cliente();
        if (invitedCliente.getStatus() == com.restaurante.model.enums.ClienteConsumoStatus.BLOCKED) throw new BusinessException("CLIENTE_CONSUMO_BLOCKED");

        SessaoConsumoParticipante existing = participanteRepository.findForUpdateBySessaoAndCliente(device.tenantId(), sessaoId, invitedCliente.getId())
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == SessaoParticipanteStatus.ACTIVE) throw new BusinessException("PARTICIPANT_ALREADY_ACTIVE");
            if (existing.getStatus() == SessaoParticipanteStatus.PENDING_APPROVAL) throw new BusinessException("PARTICIPANT_ALREADY_PENDING_APPROVAL");
            if (existing.getStatus() == SessaoParticipanteStatus.INVITED) throw new BusinessException("PARTICIPANT_ALREADY_INVITED");
        }

        TelefoneOtpService.OtpRequestResult otpResult = otpService.requestOtp(sessao.getTenant(), invitedRawPhone, OtpPurpose.ACEITAR_CONVITE_SESSAO, sessao, ip, userAgent);
        Instant now = Instant.now();

        SessaoConsumoParticipante p = existing != null ? existing : new SessaoConsumoParticipante();
        if (p.getId() == null) {
            p.setTenant(sessao.getTenant());
            p.setSessaoConsumo(sessao);
            p.setClienteConsumo(invitedCliente);
            p.setTelefoneNormalizado(invitedNormalized);
            p.setRole(SessaoParticipanteRole.MEMBER);
        }
        p.setStatus(SessaoParticipanteStatus.INVITED);
        p.setNomeExibicao(invitedNomeExibicao != null ? invitedNomeExibicao : p.getNomeExibicao());
        p.setInvitedByDeviceId(device.dispositivoId());
        p.setInvitedAt(now);
        p.setInvitationExpiresAt(otpResult.getChallenge().getExpiresAt());
        p.setEntryPolicySnapshot(effectivePolicy(sessao).name());
        participanteRepository.save(p);

        operationalEventLogService.logGeneric(
                OperationalEventType.SESSAO_PARTICIPANTE_INVITED_BY_POS,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                p.getId() != null ? p.getId() : 0L,
                OperationalOrigem.DEVICE_POS,
                "Participante convidado por POS",
                Map.of(
                        "tenantId", device.tenantId(),
                        "unidadeId", device.unidadeAtendimentoId(),
                        "deviceId", device.dispositivoId(),
                        "sessaoConsumoId", sessaoId,
                        "telefoneMascarado", invitedMasked,
                        "challengeId", otpResult.getChallenge().getId(),
                        "purpose", OtpPurpose.ACEITAR_CONVITE_SESSAO.name()
                ),
                ip,
                userAgent
        );

        return otpResult;
    }

    @Transactional
    public TelefoneOtpService.OtpRequestResult requestJoinByDevice(DevicePrincipal device,
                                                                   Long sessaoId,
                                                                   String rawPhone,
                                                                   String nomeExibicao,
                                                                   String ip,
                                                                   String userAgent) {
        requireDeviceCapability(device, DeviceCapability.ADD_SESSION_PARTICIPANT, sessaoId, null, "Request join OTP", ip, userAgent);
        SessaoConsumo sessao = requireSessaoForDevice(device, sessaoId);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");

        String phoneNormalized = phoneNormalizerService.normalizeOrThrow(rawPhone);
        String maskedPhone = phoneNormalizerService.mask(phoneNormalized);

        TelefoneOtpService.OtpRequestResult result = otpService.requestOtp(sessao.getTenant(), rawPhone, OtpPurpose.POS_ADICIONAR_PARTICIPANTE_SESSAO, sessao, ip, userAgent);

        Map<String, Object> meta = new HashMap<>();
        meta.put("tenantId", device.tenantId());
        meta.put("unidadeId", device.unidadeAtendimentoId());
        meta.put("deviceId", device.dispositivoId());
        meta.put("sessaoConsumoId", sessaoId);
        meta.put("telefoneMascarado", maskedPhone);
        meta.put("purpose", OtpPurpose.POS_ADICIONAR_PARTICIPANTE_SESSAO.name());
        meta.put("challengeId", result.getChallenge().getId());
        meta.put("smsSent", result.isSmsSent());
        operationalEventLogService.logGeneric(
                OperationalEventType.SESSAO_PARTICIPANTE_POS_JOIN_REQUESTED,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                0L,
                OperationalOrigem.DEVICE_POS,
                "POS join requested (OTP criado)",
                meta,
                ip,
                userAgent
        );
        return result;
    }

    @Transactional
    public VerifyJoinResult verifyJoinByDevice(DevicePrincipal device,
                                               Long sessaoId,
                                               Long challengeId,
                                               String rawPhone,
                                               String otp,
                                               String nomeExibicao,
                                               String ip,
                                               String userAgent) {
        requireDeviceCapability(device, DeviceCapability.ADD_SESSION_PARTICIPANT, sessaoId, null, "Verify join OTP", ip, userAgent);
        SessaoConsumo sessao = requireSessaoForDevice(device, sessaoId);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");

        String phoneNormalized = phoneNormalizerService.normalizeOrThrow(rawPhone);
        String maskedPhone = phoneNormalizerService.mask(phoneNormalized);

        TelefoneOtpChallenge ch = otpService.validateOtpPendingOrThrow(device.tenantId(), challengeId, rawPhone, otp);
        if (ch.getPurpose() != OtpPurpose.POS_ADICIONAR_PARTICIPANTE_SESSAO) throw new BusinessException("OTP_INVALID");
        Long chSessaoId = ch.getSessaoConsumo() != null ? ch.getSessaoConsumo().getId() : null;
        if (chSessaoId == null || !chSessaoId.equals(sessaoId)) throw new BusinessException("OTP_CHALLENGE_SESSION_MISMATCH");

        ClienteConsumoService.GetOrCreateResult cc = clienteConsumoService.getOrCreateByPhone(sessao.getTenant(), rawPhone, phoneNormalized);
        ClienteConsumo cliente = clienteConsumoService.markPhoneVerified(cc.cliente());

        SessaoParticipanteRole role = SessaoParticipanteRole.MEMBER;
        SessaoConsumoParticipante participante = participanteRepository.findForUpdateBySessaoAndCliente(device.tenantId(), sessaoId, cliente.getId())
                .orElse(null);
        Instant now = Instant.now();
        if (participante == null) {
            participante = new SessaoConsumoParticipante();
            participante.setTenant(sessao.getTenant());
            participante.setSessaoConsumo(sessao);
            participante.setClienteConsumo(cliente);
            participante.setTelefoneNormalizado(phoneNormalized);
            participante.setNomeExibicao(nomeExibicao);
            participante.setRole(role);
            participante.setStatus(SessaoParticipanteStatus.ACTIVE);
            participante.setJoinedAt(now);
            participante.setLastActivityAt(now);
            participante.setAddedByDevice(dispositivoOperacionalRepository.getReferenceById(device.dispositivoId()));
        } else {
            if (participante.getStatus() == SessaoParticipanteStatus.BLOCKED) throw new BusinessException("SESSAO_PARTICIPANTE_NOT_ACTIVE");
            participante.setNomeExibicao(nomeExibicao != null ? nomeExibicao : participante.getNomeExibicao());
            participante.setStatus(SessaoParticipanteStatus.ACTIVE);
            if (participante.getJoinedAt() == null) participante.setJoinedAt(now);
            participante.setLastActivityAt(now);
        }
        participante = participanteRepository.save(participante);

        otpService.consumeChallenge(device.tenantId(), challengeId);

        Map<String, Object> verifiedMeta = new HashMap<>();
        verifiedMeta.put("tenantId", device.tenantId());
        verifiedMeta.put("unidadeId", device.unidadeAtendimentoId());
        verifiedMeta.put("deviceId", device.dispositivoId());
        verifiedMeta.put("sessaoConsumoId", sessaoId);
        verifiedMeta.put("participanteId", participante.getId());
        verifiedMeta.put("clienteConsumoId", cliente.getId());
        verifiedMeta.put("role", participante.getRole().name());
        verifiedMeta.put("status", participante.getStatus().name());
        verifiedMeta.put("telefoneMascarado", maskedPhone);
        operationalEventLogService.logGeneric(
                OperationalEventType.SESSAO_PARTICIPANTE_POS_JOIN_VERIFIED,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                participante.getId(),
                OperationalOrigem.DEVICE_POS,
                "Participante adicionado por POS",
                verifiedMeta,
                ip,
                userAgent
        );

        return new VerifyJoinResult(participante.getId(), cliente.getId(), sessao.getId(), participante.getRole(), participante.getStatus(), maskedPhone, participante.getJoinedAt());
    }

    @Transactional
    public SessaoConsumoParticipante removeByDevice(DevicePrincipal device, Long sessaoId, Long participanteId, String reason, String ip, String userAgent) {
        requireDeviceCapability(device, DeviceCapability.REMOVE_SESSION_PARTICIPANT, sessaoId, participanteId, "Remove participant", ip, userAgent);
        requireReason(reason);
        SessaoConsumo sessao = requireSessaoForDevice(device, sessaoId);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");

        SessaoConsumoParticipante p = participanteRepository.findForUpdateById(device.tenantId(), participanteId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSAO_PARTICIPANTE_NOT_FOUND"));
        if (!p.getSessaoConsumo().getId().equals(sessaoId)) throw new BusinessException("SESSAO_PARTICIPANTE_WRONG_SESSION");
        if (p.getStatus() != SessaoParticipanteStatus.ACTIVE) throw new BusinessException("SESSAO_PARTICIPANTE_NOT_ACTIVE");

        if (p.getRole() == SessaoParticipanteRole.OWNER) {
            long owners = participanteRepository.countActiveOwners(device.tenantId(), sessaoId);
            if (owners <= 1) throw new BusinessException("CANNOT_REMOVE_LAST_OWNER");
        }

        p.setStatus(SessaoParticipanteStatus.REMOVED);
        p.setRemovedAt(Instant.now());
        p.setRemovedByDeviceId(device.dispositivoId());
        p.setStatusChangedReason(sanitizeReason(reason));
        SessaoConsumoParticipante saved = participanteRepository.save(p);

        Map<String, Object> meta = new HashMap<>();
        meta.put("tenantId", device.tenantId());
        meta.put("unidadeId", device.unidadeAtendimentoId());
        meta.put("deviceId", device.dispositivoId());
        meta.put("sessaoConsumoId", sessaoId);
        meta.put("participanteId", saved.getId());
        meta.put("clienteConsumoId", saved.getClienteConsumo() != null ? saved.getClienteConsumo().getId() : null);
        meta.put("oldStatus", SessaoParticipanteStatus.ACTIVE.name());
        meta.put("newStatus", saved.getStatus().name());
        meta.put("reason", sanitizeReason(reason));
        operationalEventLogService.logGeneric(
                OperationalEventType.SESSAO_PARTICIPANTE_REMOVED_BY_POS,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                saved.getId(),
                OperationalOrigem.DEVICE_POS,
                "Participante removido por POS",
                meta,
                ip,
                userAgent
        );
        return saved;
    }

    @Transactional
    public SessaoConsumoParticipante promoteByDevice(DevicePrincipal device, Long sessaoId, Long participanteId, SessaoParticipanteRole targetRole, String reason, String ip, String userAgent) {
        requireDeviceCapability(device, DeviceCapability.PROMOTE_SESSION_PARTICIPANT, sessaoId, participanteId, "Promote participant", ip, userAgent);
        requireReason(reason);
        if (targetRole != SessaoParticipanteRole.OWNER) throw new BusinessException("PARTICIPANT_ROLE_NOT_ALLOWED");
        SessaoConsumo sessao = requireSessaoForDevice(device, sessaoId);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");

        SessaoConsumoParticipante p = participanteRepository.findForUpdateById(device.tenantId(), participanteId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSAO_PARTICIPANTE_NOT_FOUND"));
        if (!p.getSessaoConsumo().getId().equals(sessaoId)) throw new BusinessException("SESSAO_PARTICIPANTE_WRONG_SESSION");
        if (p.getStatus() != SessaoParticipanteStatus.ACTIVE) throw new BusinessException("SESSAO_PARTICIPANTE_NOT_ACTIVE");

        SessaoParticipanteRole oldRole = p.getRole();
        if (oldRole == SessaoParticipanteRole.OWNER) return p;

        p.setRole(SessaoParticipanteRole.OWNER);
        p.setPromotedAt(Instant.now());
        p.setPromotedByDeviceId(device.dispositivoId());
        p.setRoleChangedReason(sanitizeReason(reason));
        SessaoConsumoParticipante saved = participanteRepository.save(p);

        Map<String, Object> meta = new HashMap<>();
        meta.put("tenantId", device.tenantId());
        meta.put("unidadeId", device.unidadeAtendimentoId());
        meta.put("deviceId", device.dispositivoId());
        meta.put("sessaoConsumoId", sessaoId);
        meta.put("participanteId", saved.getId());
        meta.put("clienteConsumoId", saved.getClienteConsumo() != null ? saved.getClienteConsumo().getId() : null);
        meta.put("oldRole", oldRole.name());
        meta.put("newRole", saved.getRole().name());
        meta.put("reason", sanitizeReason(reason));
        operationalEventLogService.logGeneric(
                OperationalEventType.SESSAO_PARTICIPANTE_PROMOTED_BY_POS,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                saved.getId(),
                OperationalOrigem.DEVICE_POS,
                "Participante promovido por POS",
                meta,
                ip,
                userAgent
        );
        return saved;
    }

    @Transactional
    public SessaoConsumoParticipante demoteByDevice(DevicePrincipal device, Long sessaoId, Long participanteId, SessaoParticipanteRole targetRole, String reason, String ip, String userAgent) {
        requireDeviceCapability(device, DeviceCapability.DEMOTE_SESSION_PARTICIPANT, sessaoId, participanteId, "Demote participant", ip, userAgent);
        requireReason(reason);
        if (targetRole != SessaoParticipanteRole.MEMBER && targetRole != SessaoParticipanteRole.GUEST) throw new BusinessException("PARTICIPANT_ROLE_NOT_ALLOWED");
        SessaoConsumo sessao = requireSessaoForDevice(device, sessaoId);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");

        SessaoConsumoParticipante p = participanteRepository.findForUpdateById(device.tenantId(), participanteId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSAO_PARTICIPANTE_NOT_FOUND"));
        if (!p.getSessaoConsumo().getId().equals(sessaoId)) throw new BusinessException("SESSAO_PARTICIPANTE_WRONG_SESSION");
        if (p.getStatus() != SessaoParticipanteStatus.ACTIVE) throw new BusinessException("SESSAO_PARTICIPANTE_NOT_ACTIVE");

        SessaoParticipanteRole oldRole = p.getRole();
        if (oldRole != SessaoParticipanteRole.OWNER) {
            p.setRole(targetRole);
        } else {
            long owners = participanteRepository.countActiveOwners(device.tenantId(), sessaoId);
            if (owners <= 1) throw new BusinessException("CANNOT_DEMOTE_LAST_OWNER");
            p.setRole(targetRole);
        }

        p.setDemotedAt(Instant.now());
        p.setDemotedByDeviceId(device.dispositivoId());
        p.setRoleChangedReason(sanitizeReason(reason));
        SessaoConsumoParticipante saved = participanteRepository.save(p);

        Map<String, Object> meta = new HashMap<>();
        meta.put("tenantId", device.tenantId());
        meta.put("unidadeId", device.unidadeAtendimentoId());
        meta.put("deviceId", device.dispositivoId());
        meta.put("sessaoConsumoId", sessaoId);
        meta.put("participanteId", saved.getId());
        meta.put("clienteConsumoId", saved.getClienteConsumo() != null ? saved.getClienteConsumo().getId() : null);
        meta.put("oldRole", oldRole.name());
        meta.put("newRole", saved.getRole().name());
        meta.put("reason", sanitizeReason(reason));
        operationalEventLogService.logGeneric(
                OperationalEventType.SESSAO_PARTICIPANTE_DEMOTED_BY_POS,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                saved.getId(),
                OperationalOrigem.DEVICE_POS,
                "Participante rebaixado por POS",
                meta,
                ip,
                userAgent
        );
        return saved;
    }

    @Transactional
    public SessaoConsumoParticipante blockByDevice(DevicePrincipal device, Long sessaoId, Long participanteId, String reason, String ip, String userAgent) {
        requireDeviceCapability(device, DeviceCapability.BLOCK_SESSION_PARTICIPANT, sessaoId, participanteId, "Block participant", ip, userAgent);
        requireReason(reason);
        SessaoConsumo sessao = requireSessaoForDevice(device, sessaoId);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");

        SessaoConsumoParticipante p = participanteRepository.findForUpdateById(device.tenantId(), participanteId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSAO_PARTICIPANTE_NOT_FOUND"));
        if (!p.getSessaoConsumo().getId().equals(sessaoId)) throw new BusinessException("SESSAO_PARTICIPANTE_WRONG_SESSION");
        if (p.getStatus() != SessaoParticipanteStatus.ACTIVE) throw new BusinessException("SESSAO_PARTICIPANTE_NOT_ACTIVE");

        if (p.getRole() == SessaoParticipanteRole.OWNER) {
            long owners = participanteRepository.countActiveOwners(device.tenantId(), sessaoId);
            if (owners <= 1) throw new BusinessException("CANNOT_BLOCK_LAST_OWNER");
        }

        p.setStatus(SessaoParticipanteStatus.BLOCKED);
        p.setBlockedAt(Instant.now());
        p.setBlockedByDeviceId(device.dispositivoId());
        p.setStatusChangedReason(sanitizeReason(reason));
        SessaoConsumoParticipante saved = participanteRepository.save(p);

        Map<String, Object> meta = new HashMap<>();
        meta.put("tenantId", device.tenantId());
        meta.put("unidadeId", device.unidadeAtendimentoId());
        meta.put("deviceId", device.dispositivoId());
        meta.put("sessaoConsumoId", sessaoId);
        meta.put("participanteId", saved.getId());
        meta.put("clienteConsumoId", saved.getClienteConsumo() != null ? saved.getClienteConsumo().getId() : null);
        meta.put("oldStatus", SessaoParticipanteStatus.ACTIVE.name());
        meta.put("newStatus", saved.getStatus().name());
        meta.put("reason", sanitizeReason(reason));
        operationalEventLogService.logGeneric(
                OperationalEventType.SESSAO_PARTICIPANTE_BLOCKED_BY_POS,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                saved.getId(),
                OperationalOrigem.DEVICE_POS,
                "Participante bloqueado por POS",
                meta,
                ip,
                userAgent
        );
        return saved;
    }

    @Transactional
    public SessaoConsumoParticipante restoreByDevice(DevicePrincipal device, Long sessaoId, Long participanteId, String reason, String ip, String userAgent) {
        requireDeviceCapability(device, DeviceCapability.RESTORE_SESSION_PARTICIPANT, sessaoId, participanteId, "Restore participant", ip, userAgent);
        requireReason(reason);
        SessaoConsumo sessao = requireSessaoForDevice(device, sessaoId);
        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");

        SessaoConsumoParticipante p = participanteRepository.findForUpdateById(device.tenantId(), participanteId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSAO_PARTICIPANTE_NOT_FOUND"));
        if (!p.getSessaoConsumo().getId().equals(sessaoId)) throw new BusinessException("SESSAO_PARTICIPANTE_WRONG_SESSION");
        if (p.getStatus() != SessaoParticipanteStatus.BLOCKED && p.getStatus() != SessaoParticipanteStatus.REMOVED) {
            throw new BusinessException("SESSAO_PARTICIPANTE_NOT_ACTIVE");
        }

        SessaoParticipanteStatus oldStatus = p.getStatus();
        SessaoParticipanteRole oldRole = p.getRole();

        p.setStatus(SessaoParticipanteStatus.ACTIVE);
        p.setRole(SessaoParticipanteRole.MEMBER);
        p.setRestoredByDeviceId(device.dispositivoId());
        p.setStatusChangedReason(sanitizeReason(reason));
        p.setRoleChangedReason(sanitizeReason(reason));
        p.setLastActivityAt(Instant.now());
        if (p.getJoinedAt() == null) p.setJoinedAt(Instant.now());
        SessaoConsumoParticipante saved = participanteRepository.save(p);

        Map<String, Object> meta = new HashMap<>();
        meta.put("tenantId", device.tenantId());
        meta.put("unidadeId", device.unidadeAtendimentoId());
        meta.put("deviceId", device.dispositivoId());
        meta.put("sessaoConsumoId", sessaoId);
        meta.put("participanteId", saved.getId());
        meta.put("clienteConsumoId", saved.getClienteConsumo() != null ? saved.getClienteConsumo().getId() : null);
        meta.put("oldStatus", oldStatus.name());
        meta.put("newStatus", saved.getStatus().name());
        meta.put("oldRole", oldRole.name());
        meta.put("newRole", saved.getRole().name());
        meta.put("reason", sanitizeReason(reason));
        operationalEventLogService.logGeneric(
                OperationalEventType.SESSAO_PARTICIPANTE_RESTORED_BY_POS,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                saved.getId(),
                OperationalOrigem.DEVICE_POS,
                "Participante restaurado por POS",
                meta,
                ip,
                userAgent
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public SessaoConsumoParticipante requireActiveParticipant(Long tenantId, Long sessaoId, Long participanteId) {
        SessaoConsumoParticipante p = participanteRepository.findByTenant_IdAndId(tenantId, participanteId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSAO_PARTICIPANTE_NOT_FOUND"));
        if (!p.getSessaoConsumo().getId().equals(sessaoId)) throw new BusinessException("SESSAO_PARTICIPANTE_WRONG_SESSION");
        if (p.getStatus() != SessaoParticipanteStatus.ACTIVE) throw new BusinessException("SESSAO_PARTICIPANTE_NOT_ACTIVE");
        return p;
    }

    private SessaoConsumo requireSessaoForDevice(DevicePrincipal device, Long sessaoId) {
        SessaoConsumo sessao = sessaoConsumoRepository.findByIdAndTenantId(sessaoId, device.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("SessaoConsumo", "id", sessaoId));

        Long sessaoUnidade = sessao.getUnidadeAtendimento() != null ? sessao.getUnidadeAtendimento().getId() : null;
        if (sessaoUnidade != null && device.unidadeAtendimentoId() != null && !sessaoUnidade.equals(device.unidadeAtendimentoId())) {
            throw new ResourceNotFoundException("SessaoConsumo", "id", sessaoId);
        }
        return sessao;
    }

    private void requireDeviceCapability(DevicePrincipal device,
                                         DeviceCapability capability,
                                         Long sessaoId,
                                         Long participanteId,
                                         String reason,
                                         String ip,
                                         String userAgent) {
        if (device != null && device.capabilities() != null && device.capabilities().contains(capability)) return;

        operationalEventLogService.logGeneric(
                OperationalEventType.SESSAO_PARTICIPANTE_PERMISSION_DENIED,
                OperationalEntityType.DISPOSITIVO_OPERACIONAL,
                device != null && device.dispositivoId() != null ? device.dispositivoId() : 0L,
                OperationalOrigem.DEVICE_POS,
                "Participant capability denied",
                deniedMeta(device, sessaoId, participanteId, capability, reason),
                ip,
                userAgent
        );
        throw new DeviceForbiddenException("DEVICE_CAPABILITY_REQUIRED");
    }

    private Map<String, Object> deniedMeta(DevicePrincipal device,
                                          Long sessaoId,
                                          Long participanteId,
                                          DeviceCapability capability,
                                          String reason) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("tenantId", device != null ? device.tenantId() : null);
        meta.put("unidadeId", device != null ? device.unidadeAtendimentoId() : null);
        meta.put("deviceId", device != null ? device.dispositivoId() : null);
        meta.put("sessaoConsumoId", sessaoId);
        meta.put("participanteId", participanteId);
        meta.put("capability", capability != null ? capability.name() : null);
        meta.put("reason", reason);
        return meta;
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new BusinessException("REASON_REQUIRED");
    }

    private String sanitizeReason(String reason) {
        if (reason == null) return null;
        String trimmed = reason.trim();
        if (trimmed.length() > 255) trimmed = trimmed.substring(0, 255);
        return trimmed;
    }

    private SessaoParticipantEntryPolicy effectivePolicy(SessaoConsumo sessao) {
        SessaoParticipantEntryPolicy p = sessao != null ? sessao.getParticipantEntryPolicy() : null;
        return p != null ? p : SessaoParticipantEntryPolicy.OTP_AUTO_JOIN;
    }

    private void applyStatusByPolicyOnVerify(SessaoConsumoParticipante participante, SessaoParticipantEntryPolicy policy, Instant now) {
        if (policy == SessaoParticipantEntryPolicy.OTP_REQUIRES_OWNER_APPROVAL) {
            participante.setStatus(SessaoParticipanteStatus.PENDING_APPROVAL);
            participante.setApprovalRequestedAt(now);
            participante.setEntryPolicySnapshot(policy.name());
            if (participante.getJoinedAt() == null) participante.setJoinedAt(now);
            participante.setLastActivityAt(now);
            return;
        }

        participante.setStatus(SessaoParticipanteStatus.ACTIVE);
        participante.setEntryPolicySnapshot(policy != null ? policy.name() : SessaoParticipantEntryPolicy.OTP_AUTO_JOIN.name());
        if (participante.getJoinedAt() == null) participante.setJoinedAt(now);
        participante.setLastActivityAt(now);
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
