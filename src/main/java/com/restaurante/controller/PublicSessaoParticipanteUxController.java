package com.restaurante.controller;

import com.restaurante.consumo.participante.service.SessaoConsumoParticipanteService;
import com.restaurante.consumo.participante.service.SessaoOwnerActionTokenService;
import com.restaurante.consumo.participante.service.SessaoParticipanteExtendedListService;
import com.restaurante.dto.request.PublicOwnerOtpVerifyRequest;
import com.restaurante.dto.request.PublicOwnerTokenActionRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PublicOwnerActionTokenResponse;
import com.restaurante.dto.response.SessaoParticipantePageResponse;
import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Prompt 41.4 — UX Hardening: OwnerActionToken + listagens ampliadas (público/OWNER).
 */
@RestController
@RequestMapping("/public/q/{token}")
@RequiredArgsConstructor
@Tag(name = "QR Público - UX Hardening (41.4)", description = "Owner action token e listagens ampliadas de participantes")
public class PublicSessaoParticipanteUxController {

    private final SessaoConsumoParticipanteService participanteService;
    private final SessaoOwnerActionTokenService ownerActionTokenService;
    private final SessaoParticipanteExtendedListService extendedListService;

    /**
     * Verifica OTP do OWNER e emite ownerActionToken curto.
     * O token é retornado UMA SÓ VEZ — cliente deve guardá-lo temporariamente.
     */
    @PostMapping("/owner-auth/otp/verify")
    @Operation(summary = "Verificar OTP do OWNER e obter ownerActionToken para ações múltiplas")
    public ResponseEntity<ApiResponse<PublicOwnerActionTokenResponse>> verifyOwnerOtpAndIssueToken(
            @PathVariable String token,
            @Valid @RequestBody PublicOwnerOtpVerifyRequest request,
            HttpServletRequest http
    ) {
        String ip = ip(http);
        String ua = ua(http);

        // Valida OTP e obtém participante OWNER (reutiliza lógica existente)
        var owner = participanteService.assertOwnerByOtp(
                token,
                request.getOwnerChallengeId(),
                request.getOwnerTelefone(),
                request.getOtp(),
                ip, ua
        );

        // Emite token curto
        var issueResult = ownerActionTokenService.issueAfterOwnerOtp(owner, ip, ua);

        PublicOwnerActionTokenResponse resp = new PublicOwnerActionTokenResponse();
        resp.setOwnerActionToken(issueResult.rawToken()); // única vez
        resp.setTokenId(issueResult.tokenId());
        resp.setExpiresAt(issueResult.expiresAt());
        resp.setMaxUses(issueResult.maxUses());
        resp.setOwnerParticipanteId(issueResult.ownerParticipanteId());
        resp.setSessaoConsumoId(issueResult.sessaoConsumoId());

        return ResponseEntity.ok(ApiResponse.success("OwnerActionToken emitido", resp));
    }

    /**
     * Listagem unificada de participantes (público).
     * Sem ownerActionToken: apenas ACTIVE, dados mínimos.
     * Com ownerActionToken válido: visão ampliada sanitizada.
     */
    @PostMapping("/participantes/all")
    @Operation(summary = "Listagem unificada de participantes (com/sem ownerActionToken)")
    public ResponseEntity<ApiResponse<SessaoParticipantePageResponse>> listAll(
            @PathVariable String token,
            @RequestBody(required = false) PublicOwnerTokenActionRequest request,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest http
    ) {
        String ip = ip(http);
        String ua = ua(http);

        // Resolve sessão/tenant a partir do QR token
        var qrContext = participanteService.resolveQrContext(token);
        Long tenantId = qrContext.tenantId();
        Long sessaoId = qrContext.sessaoId();

        String rawOwnerToken = request != null ? request.getOwnerActionToken() : null;
        SessaoParticipanteStatus statusFilter = parseStatus(status);
        SessaoParticipanteRole roleFilter = parseRole(role);

        var page2 = extendedListService.listAllPublic(
                token, rawOwnerToken, sessaoId, tenantId,
                statusFilter, roleFilter, includeInactive,
                page, size, ip, ua
        );

        return ResponseEntity.ok(ApiResponse.success("Participantes", new SessaoParticipantePageResponse(page2)));
    }

    /**
     * Listagem de todas as pendências/convites (OWNER com token).
     */
    @PostMapping("/participantes/pending-all")
    @Operation(summary = "Listagem ampliada de pendências/convites (requer ownerActionToken)")
    public ResponseEntity<ApiResponse<SessaoParticipantePageResponse>> listPendingAll(
            @PathVariable String token,
            @RequestBody PublicOwnerTokenActionRequest request,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) Boolean canResend,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest http
    ) {
        String ip = ip(http);
        String ua = ua(http);

        var qrContext = participanteService.resolveQrContext(token);
        Long tenantId = qrContext.tenantId();
        Long sessaoId = qrContext.sessaoId();

        var tokenResult = ownerActionTokenService.validateAndUse(
                tenantId, sessaoId, request.getOwnerActionToken(), ip, ua);

        var pageResult = extendedListService.listPendingAll(
                tenantId, sessaoId, tokenResult, statuses, canResend, page, size, ip, ua);

        return ResponseEntity.ok(ApiResponse.success("Pendências", new SessaoParticipantePageResponse(pageResult)));
    }

    /**
     * Histórico de convites enviados pelo OWNER autenticado.
     */
    @PostMapping("/owner/invites")
    @Operation(summary = "Histórico de convites enviados pelo OWNER (requer ownerActionToken)")
    public ResponseEntity<ApiResponse<SessaoParticipantePageResponse>> ownerInviteHistory(
            @PathVariable String token,
            @RequestBody PublicOwnerTokenActionRequest request,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest http
    ) {
        String ip = ip(http);
        String ua = ua(http);

        var qrContext = participanteService.resolveQrContext(token);
        Long tenantId = qrContext.tenantId();
        Long sessaoId = qrContext.sessaoId();

        var tokenResult = ownerActionTokenService.validateAndUse(
                tenantId, sessaoId, request.getOwnerActionToken(), ip, ua);

        SessaoParticipanteStatus statusFilter = parseStatus(status);

        var pageResult = extendedListService.listOwnerInviteHistory(
                tenantId, sessaoId, tokenResult, statusFilter, page, size, ip, ua);

        return ResponseEntity.ok(ApiResponse.success("Convites enviados", new SessaoParticipantePageResponse(pageResult)));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private SessaoParticipanteStatus parseStatus(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return SessaoParticipanteStatus.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.restaurante.exception.BusinessException("INVALID_PARTICIPANT_STATUS_FILTER");
        }
    }

    private SessaoParticipanteRole parseRole(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return SessaoParticipanteRole.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.restaurante.exception.BusinessException("INVALID_PARTICIPANT_ROLE_FILTER");
        }
    }

    private String ip(HttpServletRequest h) { return h != null ? h.getRemoteAddr() : null; }
    private String ua(HttpServletRequest h) { return h != null ? h.getHeader("User-Agent") : null; }
}
