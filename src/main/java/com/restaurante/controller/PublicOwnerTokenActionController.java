package com.restaurante.controller;

import com.restaurante.consumo.participante.service.SessaoParticipanteOwnerTokenActionService;
import com.restaurante.consumo.participante.web.OwnerActionTokenExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Prompt 41.5 — Endpoints públicos de gestão de participantes via ownerActionToken.
 * <p>
 * Base: {@code /public/q/{token}/participantes/{participanteId}}
 * <p>
 * Token aceito:
 * 1. Header {@code X-Owner-Action-Token} (preferencial)
 * 2. Campo {@code ownerActionToken} no body (fallback)
 * 3. Query param NUNCA aceito
 */
@RestController
@RequestMapping("/public/q/{token}/participantes/{participanteId}")
@RequiredArgsConstructor
public class PublicOwnerTokenActionController {

    private final SessaoParticipanteOwnerTokenActionService actionService;
    private final OwnerActionTokenExtractor tokenExtractor;

    // =========================================================================
    // Aprovar
    // =========================================================================

    @PostMapping("/approve-by-token")
    public ResponseEntity<ApproveResponse> approveByToken(
            @PathVariable String token,
            @PathVariable Long participanteId,
            @RequestBody(required = false) OwnerTokenActionRequest body,
            HttpServletRequest request) {

        tokenExtractor.assertNotInQueryParam(request);
        String rawToken = tokenExtractor.extractRequired(request, body != null ? body.ownerActionToken() : null);
        String reason   = body != null ? body.reason() : null;
        String ip       = request.getRemoteAddr();
        String ua       = request.getHeader("User-Agent");

        var result = actionService.approveByOwnerToken(token, participanteId, rawToken, reason, ip, ua);
        return ResponseEntity.ok(new ApproveResponse(
                result.participanteId(),
                result.status().name(),
                true,
                result.approvedAt(),
                result.ownerParticipanteId()
        ));
    }

    // =========================================================================
    // Rejeitar
    // =========================================================================

    @PostMapping("/reject-by-token")
    public ResponseEntity<RejectResponse> rejectByToken(
            @PathVariable String token,
            @PathVariable Long participanteId,
            @RequestBody(required = false) OwnerTokenActionRequest body,
            HttpServletRequest request) {

        tokenExtractor.assertNotInQueryParam(request);
        String rawToken = tokenExtractor.extractRequired(request, body != null ? body.ownerActionToken() : null);
        String reason   = body != null ? body.reason() : null;
        String ip       = request.getRemoteAddr();
        String ua       = request.getHeader("User-Agent");

        var result = actionService.rejectByOwnerToken(token, participanteId, rawToken, reason, ip, ua);
        return ResponseEntity.ok(new RejectResponse(
                result.participanteId(),
                result.status().name(),
                true,
                result.rejectedAt(),
                result.ownerParticipanteId()
        ));
    }

    // =========================================================================
    // Cancelar
    // =========================================================================

    @PostMapping("/cancel-by-token")
    public ResponseEntity<CancelResponse> cancelByToken(
            @PathVariable String token,
            @PathVariable Long participanteId,
            @RequestBody(required = false) OwnerTokenActionRequest body,
            HttpServletRequest request) {

        tokenExtractor.assertNotInQueryParam(request);
        String rawToken = tokenExtractor.extractRequired(request, body != null ? body.ownerActionToken() : null);
        String reason   = body != null ? body.reason() : null;
        String ip       = request.getRemoteAddr();
        String ua       = request.getHeader("User-Agent");

        var result = actionService.cancelByOwnerToken(token, participanteId, rawToken, reason, ip, ua);
        return ResponseEntity.ok(new CancelResponse(
                result.participanteId(),
                result.status().name(),
                result.wasCancelled(),
                result.cancelledAt(),
                result.ownerParticipanteId()
        ));
    }

    // =========================================================================
    // Reenviar convite
    // =========================================================================

    @PostMapping("/resend-invite-by-token")
    public ResponseEntity<ResendResponse> resendInviteByToken(
            @PathVariable String token,
            @PathVariable Long participanteId,
            @RequestBody(required = false) OwnerTokenActionRequest body,
            HttpServletRequest request) {

        tokenExtractor.assertNotInQueryParam(request);
        String rawToken = tokenExtractor.extractRequired(request, body != null ? body.ownerActionToken() : null);
        String ip = request.getRemoteAddr();
        String ua = request.getHeader("User-Agent");

        var result = actionService.resendInviteByOwnerToken(token, participanteId, rawToken, ip, ua);
        return ResponseEntity.ok(new ResendResponse(
                result.participanteId(),
                result.status().name(),
                result.resent(),
                result.resendCount(),
                result.lastResendAt(),
                result.canResend()
        ));
    }

    // =========================================================================
    // DTOs locais (records)
    // =========================================================================

    public record OwnerTokenActionRequest(String ownerActionToken, String reason) {}

    public record ApproveResponse(
            Long participanteId,
            String status,
            boolean approved,
            Instant approvedAt,
            Long ownerParticipanteId) {}

    public record RejectResponse(
            Long participanteId,
            String status,
            boolean rejected,
            Instant rejectedAt,
            Long ownerParticipanteId) {}

    public record CancelResponse(
            Long participanteId,
            String status,
            boolean wasCancelled,
            Instant cancelledAt,
            Long ownerParticipanteId) {}

    public record ResendResponse(
            Long participanteId,
            String status,
            boolean resent,
            int resendCount,
            Instant lastResendAt,
            boolean canResend) {}
}
