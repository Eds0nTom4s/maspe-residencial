package com.restaurante.consumo.participante.service;

import com.restaurante.config.SessaoOwnerActionTokenProperties;
import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.consumo.participante.entity.SessaoOwnerActionToken;
import com.restaurante.consumo.participante.repository.SessaoOwnerActionTokenRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.SessaoOwnerActionTokenPurpose;
import com.restaurante.model.enums.SessaoOwnerActionTokenStatus;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Prompt 41.4 — Owner Action Token Service.
 * <p>
 * Emite um token curto após OTP do OWNER ser verificado.
 * O token permite múltiplas ações de gestão de sessão sem repetir OTP.
 * <p>
 * SEGURANÇA:
 * - Apenas o hash SHA-256 (+ pepper) é persistido.
 * - O token em texto claro é retornado UMA SÓ VEZ, nunca logado.
 * - Token é limitado à sessão e ao OWNER.
 * - Token tem TTL e maxUses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessaoOwnerActionTokenService {

    private static final String JOB_NAME = "SessaoOwnerActionToken";

    private final SessaoOwnerActionTokenRepository tokenRepository;
    private final SessaoOwnerActionTokenProperties tokenProps;
    private final OperationalEventLogService operationalEventLogService;

    // -------------------------------------------------------------------------
    // Emissão
    // -------------------------------------------------------------------------

    /**
     * Emite um ownerActionToken após OTP do OWNER validado.
     * Retorna o token em texto claro (única vez).
     */
    @Transactional
    public IssueResult issueAfterOwnerOtp(SessaoConsumoParticipante ownerParticipante,
                                          String ip,
                                          String userAgent) {
        Instant now = Instant.now();
        SessaoConsumo sessao = ownerParticipante.getSessaoConsumo();
        Tenant tenant = ownerParticipante.getTenant();

        String rawToken = generateSecureToken();
        String hash = hashToken(rawToken);

        SessaoOwnerActionToken token = new SessaoOwnerActionToken();
        token.setTenant(tenant);
        token.setSessaoConsumo(sessao);
        token.setOwnerParticipante(ownerParticipante);
        token.setClienteConsumo(ownerParticipante.getClienteConsumo());
        token.setTokenHash(hash);
        token.setPurpose(SessaoOwnerActionTokenPurpose.OWNER_SESSION_MANAGEMENT);
        token.setStatus(SessaoOwnerActionTokenStatus.ACTIVE);
        token.setExpiresAt(now.plusSeconds((long) tokenProps.getTtlMinutes() * 60L));
        token.setMaxUses(tokenProps.getMaxUses());
        token.setClientIp(ip);
        token.setUserAgent(userAgent != null && userAgent.length() > 255
                ? userAgent.substring(0, 255) : userAgent);

        SessaoOwnerActionToken saved = tokenRepository.save(token);

        operationalEventLogService.logPublicEvent(
                tenant, sessao.getInstituicao(), sessao.getUnidadeAtendimento(),
                sessao.getMesa(), null,
                OperationalEventType.SESSAO_OWNER_ACTION_TOKEN_ISSUED,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                ownerParticipante.getId(),
                OperationalOrigem.QR_PUBLICO,
                "OwnerActionToken emitido",
                Map.of(
                        "tenantId", tenant.getId(),
                        "sessaoConsumoId", sessao.getId(),
                        "ownerParticipanteId", ownerParticipante.getId(),
                        "tokenId", saved.getId(),
                        "purpose", saved.getPurpose().name(),
                        "expiresAt", saved.getExpiresAt(),
                        "maxUses", saved.getMaxUses()
                ),
                ip, userAgent
        );

        // rawToken retornado UMA SÓ VEZ — não logado, não persistido
        return new IssueResult(rawToken, saved.getId(), saved.getExpiresAt(), saved.getMaxUses(),
                ownerParticipante.getId(), sessao.getId());
    }

    // -------------------------------------------------------------------------
    // Validação
    // -------------------------------------------------------------------------

    /**
     * Valida e usa o ownerActionToken.
     * Incrementa useCount, verifica TTL, maxUses, sessão e tenant.
     * Lança BusinessException em caso de falha.
     */
    @Transactional
    public ValidateResult validateAndUse(Long tenantId, Long sessaoId, String rawToken, String ip, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException("OWNER_ACTION_TOKEN_REQUIRED");
        }

        String hash = hashToken(rawToken);
        SessaoOwnerActionToken token = tokenRepository.findForUpdateByHash(tenantId, hash)
                .orElseThrow(() -> new BusinessException("OWNER_ACTION_TOKEN_INVALID"));

        Instant now = Instant.now();

        // Status checks
        if (token.getStatus() == SessaoOwnerActionTokenStatus.REVOKED) {
            throw new BusinessException("OWNER_ACTION_TOKEN_REVOKED");
        }
        if (token.getStatus() == SessaoOwnerActionTokenStatus.CONSUMED) {
            throw new BusinessException("OWNER_ACTION_TOKEN_INVALID");
        }
        if (token.getStatus() == SessaoOwnerActionTokenStatus.EXPIRED
                || token.isExpired(now)) {
            if (token.getStatus() != SessaoOwnerActionTokenStatus.EXPIRED) {
                token.setStatus(SessaoOwnerActionTokenStatus.EXPIRED);
                tokenRepository.save(token);
            }
            throw new BusinessException("OWNER_ACTION_TOKEN_EXPIRED");
        }

        // Sessão deve corresponder
        if (!token.getSessaoConsumo().getId().equals(sessaoId)) {
            throw new BusinessException("OWNER_ACTION_TOKEN_SESSION_MISMATCH");
        }

        // MaxUses
        if (token.hasExceededMaxUses()) {
            throw new BusinessException("OWNER_ACTION_TOKEN_MAX_USES_EXCEEDED");
        }

        // Incrementar uso
        token.setUseCount(token.getUseCount() + 1);
        token.setLastUsedAt(now);

        // Marcar CONSUMED se atingiu maxUses exatamente neste uso
        if (token.getMaxUses() != null && token.getUseCount() >= token.getMaxUses()) {
            token.setStatus(SessaoOwnerActionTokenStatus.CONSUMED);
            token.setConsumedAt(now);
        }

        SessaoOwnerActionToken saved = tokenRepository.save(token);

        operationalEventLogService.logPublicEvent(
                saved.getTenant(), saved.getSessaoConsumo().getInstituicao(),
                saved.getSessaoConsumo().getUnidadeAtendimento(), saved.getSessaoConsumo().getMesa(), null,
                OperationalEventType.SESSAO_OWNER_ACTION_TOKEN_USED,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                saved.getOwnerParticipante().getId(),
                OperationalOrigem.QR_PUBLICO,
                "OwnerActionToken usado",
                Map.of(
                        "tenantId", tenantId,
                        "sessaoConsumoId", sessaoId,
                        "ownerParticipanteId", saved.getOwnerParticipante().getId(),
                        "tokenId", saved.getId(),
                        "useCount", saved.getUseCount(),
                        "status", saved.getStatus().name()
                ),
                ip, userAgent
        );

        return new ValidateResult(saved.getOwnerParticipante(), saved.getSessaoConsumo(), saved.getTenant());
    }

    // -------------------------------------------------------------------------
    // Revogação
    // -------------------------------------------------------------------------

    @Transactional
    public void revokeToken(Long tenantId, Long tokenId, String ip, String userAgent) {
        SessaoOwnerActionToken token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new BusinessException("OWNER_ACTION_TOKEN_INVALID"));

        if (!token.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("OWNER_ACTION_TOKEN_INVALID");
        }

        Instant now = Instant.now();
        token.setStatus(SessaoOwnerActionTokenStatus.REVOKED);
        token.setRevokedAt(now);
        tokenRepository.save(token);

        operationalEventLogService.logPublicEvent(
                token.getTenant(), token.getSessaoConsumo().getInstituicao(),
                token.getSessaoConsumo().getUnidadeAtendimento(), token.getSessaoConsumo().getMesa(), null,
                OperationalEventType.SESSAO_OWNER_ACTION_TOKEN_REVOKED,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                token.getOwnerParticipante().getId(),
                OperationalOrigem.QR_PUBLICO,
                "OwnerActionToken revogado",
                Map.of("tenantId", tenantId, "tokenId", tokenId),
                ip, userAgent
        );
    }

    /**
     * Prompt 41.5 — Revoga todos os tokens ACTIVE de uma sessão ao fechá-la.
     * Operação idempotente e tolerante a falhas de auditoria.
     *
     * @return número de tokens revogados
     */
    @Transactional
    public int revokeActiveTokensBySessao(Long tenantId, Long sessaoId, String reason, String ip, String userAgent) {
        var tokens = tokenRepository.findActiveTokensBySessaoForUpdate(tenantId, sessaoId);
        if (tokens.isEmpty()) return 0;

        Instant now = Instant.now();
        for (SessaoOwnerActionToken t : tokens) {
            t.setStatus(SessaoOwnerActionTokenStatus.REVOKED);
            t.setRevokedAt(now);
        }
        tokenRepository.saveAll(tokens);

        try {
            // Auditoria agregada — não audita tokenHash individual
            var sessao = tokens.get(0).getSessaoConsumo();
            var tenant  = tokens.get(0).getTenant();
            operationalEventLogService.logPublicEvent(
                    tenant, sessao.getInstituicao(), sessao.getUnidadeAtendimento(), sessao.getMesa(), null,
                    OperationalEventType.SESSAO_OWNER_ACTION_TOKENS_REVOKED_BY_SESSION_CLOSE,
                    OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                    sessaoId,
                    OperationalOrigem.SYSTEM,
                    "Tokens revogados ao fechar sessão",
                    Map.of(
                            "tenantId", tenantId,
                            "sessaoConsumoId", sessaoId,
                            "revokedCount", tokens.size(),
                            "reason", reason != null ? reason : "SESSION_CLOSE"
                    ),
                    ip, userAgent
            );
        } catch (Exception e) {
            log.warn("[OwnerActionToken] Falha ao auditar revogação em massa (sessaoId={}): {}", sessaoId, e.getMessage());
        }

        log.info("[OwnerActionToken] {} token(s) revogado(s) ao fechar sessão {} (tenant={})", tokens.size(), sessaoId, tenantId);
        return tokens.size();
    }

    // -------------------------------------------------------------------------
    // Geração e hashing
    // -------------------------------------------------------------------------

    /** Gera token aleatório forte (UUID v4 + UUID v4 concatenados para 72 chars). */
    public String generateSecureToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    /** SHA-256(pepper + ":" + rawToken) — pepper evita rainbow tables */
    public String hashToken(String rawToken) {
        try {
            String input = tokenProps.getHashPepper() + ":" + rawToken;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // -------------------------------------------------------------------------
    // Records de resultado
    // -------------------------------------------------------------------------

    public record IssueResult(
            String rawToken,         // NUNCA logar, retornar apenas uma vez
            Long tokenId,
            Instant expiresAt,
            Integer maxUses,
            Long ownerParticipanteId,
            Long sessaoConsumoId
    ) {}

    public record ValidateResult(
            SessaoConsumoParticipante ownerParticipante,
            SessaoConsumo sessaoConsumo,
            Tenant tenant
    ) {}
}
