package com.restaurante.controller;

import com.restaurante.consumo.participante.service.SessaoConsumoParticipanteService;
import com.restaurante.dto.request.DeviceSessaoParticipanteOtpRequest;
import com.restaurante.dto.request.DeviceSessaoParticipanteOtpVerifyRequest;
import com.restaurante.dto.request.DeviceSessaoParticipanteInviteRequest;
import com.restaurante.dto.request.DeviceSessaoEntryPolicyChangeRequest;
import com.restaurante.dto.request.DeviceSessaoParticipanteOptionalReasonRequest;
import com.restaurante.dto.request.DeviceSessaoParticipanteReasonRequest;
import com.restaurante.dto.request.DeviceSessaoParticipanteRoleChangeRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.DeviceSessaoEntryPolicyResponse;
import com.restaurante.dto.response.DeviceSessaoParticipanteJoinVerifyResponse;
import com.restaurante.dto.response.DeviceSessaoParticipanteManagementResponse;
import com.restaurante.dto.response.DeviceSessaoParticipanteOtpChallengeResponse;
import com.restaurante.dto.response.DeviceSessaoParticipantesResponse;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.security.device.DevicePrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/device/sessoes-consumo/{sessaoId}/participantes")
@RequiredArgsConstructor
@Tag(name = "Device - Participantes", description = "Gestão assistida de participantes da SessaoConsumo (Prompt 41.1)")
public class DeviceSessaoParticipanteController {

    private final SessaoConsumoParticipanteService participanteService;

    @GetMapping
    @Operation(summary = "Listar participantes (resumo) de uma sessão")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipantesResponse>> list(@PathVariable Long sessaoId, HttpServletRequest http) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        var list = participanteService.listByDevice(device, sessaoId, ip, ua);
        DeviceSessaoParticipantesResponse resp = new DeviceSessaoParticipantesResponse();
        resp.setSessaoConsumoId(sessaoId);
        resp.setParticipantes(list.stream().map(p -> {
            DeviceSessaoParticipantesResponse.Item i = new DeviceSessaoParticipantesResponse.Item();
            i.setParticipanteId(p.getId());
            i.setNomeExibicao(p.getNomeExibicao());
            i.setRole(p.getRole());
            i.setStatus(p.getStatus());
            i.setJoinedAt(p.getJoinedAt());
            i.setLastActivityAt(p.getLastActivityAt());
            return i;
        }).toList());
        return ResponseEntity.ok(ApiResponse.success("Participantes", resp));
    }

    @GetMapping("/pending")
    @Operation(summary = "Listar participantes pendentes (PENDING_APPROVAL) de uma sessão")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipantesResponse>> listPending(@PathVariable Long sessaoId, HttpServletRequest http) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var list = participanteService.listPendingByDevice(device, sessaoId, ip, ua);
        DeviceSessaoParticipantesResponse resp = new DeviceSessaoParticipantesResponse();
        resp.setSessaoConsumoId(sessaoId);
        resp.setParticipantes(list.stream().map(p -> {
            DeviceSessaoParticipantesResponse.Item i = new DeviceSessaoParticipantesResponse.Item();
            i.setParticipanteId(p.getId());
            i.setNomeExibicao(p.getNomeExibicao());
            i.setRole(p.getRole());
            i.setStatus(p.getStatus());
            i.setJoinedAt(p.getJoinedAt());
            i.setLastActivityAt(p.getLastActivityAt());
            i.setExpiresAt(p.getExpiresAt());
            i.setResendCount(p.getResendCount());
            i.setLastResendAt(p.getLastResendAt());
            i.setCanResend(participanteService.canResendInviteNow(p));
            return i;
        }).toList());
        return ResponseEntity.ok(ApiResponse.success("Pendentes", resp));
    }

    @PostMapping("/{participanteId}/approve")
    @Operation(summary = "Aprovar participante pendente (POS)")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipanteManagementResponse>> approve(
            @PathVariable Long sessaoId,
            @PathVariable Long participanteId,
            @Valid @RequestBody(required = false) DeviceSessaoParticipanteOptionalReasonRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        String reason = request != null ? request.getReason() : null;
        var p = participanteService.approveByDevice(device, sessaoId, participanteId, reason, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Aprovado", toMgmtResponse(p)));
    }

    @PostMapping("/{participanteId}/reject")
    @Operation(summary = "Rejeitar participante pendente (POS)")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipanteManagementResponse>> reject(
            @PathVariable Long sessaoId,
            @PathVariable Long participanteId,
            @Valid @RequestBody(required = false) DeviceSessaoParticipanteOptionalReasonRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        String reason = request != null ? request.getReason() : null;
        var p = participanteService.rejectByDevice(device, sessaoId, participanteId, reason, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Rejeitado", toMgmtResponse(p)));
    }

    @PutMapping("/participant-entry-policy")
    @Operation(summary = "Alterar política de entrada da sessão (POS)")
    public ResponseEntity<ApiResponse<DeviceSessaoEntryPolicyResponse>> changeEntryPolicy(
            @PathVariable Long sessaoId,
            @Valid @RequestBody DeviceSessaoEntryPolicyChangeRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var sessao = participanteService.changeEntryPolicyByDevice(device, sessaoId, request.getPolicy(), ip, ua);
        DeviceSessaoEntryPolicyResponse resp = new DeviceSessaoEntryPolicyResponse();
        resp.setSessaoConsumoId(sessaoId);
        resp.setPolicy(sessao.getParticipantEntryPolicy());
        resp.setUpdatedAt(sessao.getParticipantPolicyUpdatedAt());
        return ResponseEntity.ok(ApiResponse.success("Política atualizada", resp));
    }

    @PostMapping("/invite")
    @Operation(summary = "Convidar participante por telefone (POS) — envia OTP ao convidado")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipanteOtpChallengeResponse>> invite(
            @PathVariable Long sessaoId,
            @Valid @RequestBody DeviceSessaoParticipanteInviteRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var result = participanteService.inviteByDevice(device, sessaoId, request.getTelefone(), request.getNomeExibicao(), ip, ua);
        DeviceSessaoParticipanteOtpChallengeResponse resp = new DeviceSessaoParticipanteOtpChallengeResponse();
        resp.setChallengeId(result.getChallenge().getId());
        resp.setMaskedPhone(result.getMaskedPhone());
        resp.setExpiresAt(result.getChallenge().getExpiresAt());
        resp.setResendAvailableAt(result.getResendAvailableAt());
        resp.setDebugOtp(result.getDebugOtp());
        return ResponseEntity.ok(ApiResponse.success("Convite enviado (OTP)", resp));
    }

    @PostMapping("/{participanteId}/cancel")
    @Operation(summary = "Cancelar convite/pendência (POS)")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipanteManagementResponse>> cancel(
            @PathVariable Long sessaoId,
            @PathVariable Long participanteId,
            @Valid @RequestBody DeviceSessaoParticipanteReasonRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        var p = participanteService.cancelByDevice(device, sessaoId, participanteId, request.getReason(), ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Cancelado", toMgmtResponse(p)));
    }

    @PostMapping("/{participanteId}/resend-invite")
    @Operation(summary = "Reenviar convite (OTP) (POS)")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipanteOtpChallengeResponse>> resendInvite(
            @PathVariable Long sessaoId,
            @PathVariable Long participanteId,
            @Valid @RequestBody(required = false) DeviceSessaoParticipanteOptionalReasonRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        String reason = request != null ? request.getReason() : null;
        var result = participanteService.resendInviteByDevice(device, sessaoId, participanteId, reason, ip, ua);
        DeviceSessaoParticipanteOtpChallengeResponse resp = new DeviceSessaoParticipanteOtpChallengeResponse();
        resp.setChallengeId(result.getChallenge().getId());
        resp.setMaskedPhone(result.getMaskedPhone());
        resp.setExpiresAt(result.getChallenge().getExpiresAt());
        resp.setResendAvailableAt(result.getResendAvailableAt());
        resp.setDebugOtp(result.getDebugOtp());
        return ResponseEntity.ok(ApiResponse.success("Convite reenviado", resp));
    }

    @PostMapping("/{participanteId}/expire")
    @Operation(summary = "Expirar participante pendente manualmente (POS)")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipanteManagementResponse>> expire(
            @PathVariable Long sessaoId,
            @PathVariable Long participanteId,
            @Valid @RequestBody DeviceSessaoParticipanteReasonRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        var p = participanteService.expireManuallyByDevice(device, sessaoId, participanteId, request.getReason(), ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Expirado", toMgmtResponse(p)));
    }

    @PostMapping("/otp/request")
    @Operation(summary = "Solicitar OTP assistido (POS) para adicionar participante na sessão")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipanteOtpChallengeResponse>> requestJoinOtp(
            @PathVariable Long sessaoId,
            @Valid @RequestBody DeviceSessaoParticipanteOtpRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var result = participanteService.requestJoinByDevice(device, sessaoId, request.getTelefone(), request.getNomeExibicao(), ip, ua);

        DeviceSessaoParticipanteOtpChallengeResponse resp = new DeviceSessaoParticipanteOtpChallengeResponse();
        resp.setChallengeId(result.getChallenge().getId());
        resp.setMaskedPhone(result.getMaskedPhone());
        resp.setExpiresAt(result.getChallenge().getExpiresAt());
        resp.setResendAvailableAt(result.getResendAvailableAt());
        resp.setDebugOtp(result.getDebugOtp());
        return ResponseEntity.ok(ApiResponse.success("OTP solicitado", resp));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Validar OTP e adicionar participante (POS) na sessão")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipanteJoinVerifyResponse>> verifyJoinOtp(
            @PathVariable Long sessaoId,
            @Valid @RequestBody DeviceSessaoParticipanteOtpVerifyRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var r = participanteService.verifyJoinByDevice(device, sessaoId, request.getChallengeId(), request.getTelefone(), request.getOtp(), request.getNomeExibicao(), ip, ua);

        DeviceSessaoParticipanteJoinVerifyResponse resp = new DeviceSessaoParticipanteJoinVerifyResponse();
        resp.setParticipanteId(r.participanteId());
        resp.setClienteConsumoId(r.clienteConsumoId());
        resp.setSessaoConsumoId(r.sessaoConsumoId());
        resp.setRole(r.role());
        resp.setStatus(r.status());
        resp.setTelefoneMascarado(r.telefoneMascarado());
        resp.setJoinedAt(r.joinedAt());
        return ResponseEntity.ok(ApiResponse.success("Participante ativo", resp));
    }

    @PostMapping("/{participanteId}/remove")
    @Operation(summary = "Remover participante da sessão (POS)")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipanteManagementResponse>> remove(
            @PathVariable Long sessaoId,
            @PathVariable Long participanteId,
            @Valid @RequestBody DeviceSessaoParticipanteReasonRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var p = participanteService.removeByDevice(device, sessaoId, participanteId, request.getReason(), ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Participante removido", toMgmtResponse(p)));
    }

    @PostMapping("/{participanteId}/promote")
    @Operation(summary = "Promover participante (POS)")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipanteManagementResponse>> promote(
            @PathVariable Long sessaoId,
            @PathVariable Long participanteId,
            @Valid @RequestBody DeviceSessaoParticipanteRoleChangeRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var p = participanteService.promoteByDevice(device, sessaoId, participanteId, request.getTargetRole(), request.getReason(), ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Participante promovido", toMgmtResponse(p)));
    }

    @PostMapping("/{participanteId}/demote")
    @Operation(summary = "Rebaixar participante (POS)")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipanteManagementResponse>> demote(
            @PathVariable Long sessaoId,
            @PathVariable Long participanteId,
            @Valid @RequestBody DeviceSessaoParticipanteRoleChangeRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var p = participanteService.demoteByDevice(device, sessaoId, participanteId, request.getTargetRole(), request.getReason(), ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Participante rebaixado", toMgmtResponse(p)));
    }

    @PostMapping("/{participanteId}/block")
    @Operation(summary = "Bloquear participante (POS)")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipanteManagementResponse>> block(
            @PathVariable Long sessaoId,
            @PathVariable Long participanteId,
            @Valid @RequestBody DeviceSessaoParticipanteReasonRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var p = participanteService.blockByDevice(device, sessaoId, participanteId, request.getReason(), ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Participante bloqueado", toMgmtResponse(p)));
    }

    @PostMapping("/{participanteId}/restore")
    @Operation(summary = "Restaurar participante (POS)")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipanteManagementResponse>> restore(
            @PathVariable Long sessaoId,
            @PathVariable Long participanteId,
            @Valid @RequestBody DeviceSessaoParticipanteReasonRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var p = participanteService.restoreByDevice(device, sessaoId, participanteId, request.getReason(), ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Participante restaurado", toMgmtResponse(p)));
    }

    private DeviceSessaoParticipanteManagementResponse toMgmtResponse(com.restaurante.consumo.participante.entity.SessaoConsumoParticipante p) {
        DeviceSessaoParticipanteManagementResponse r = new DeviceSessaoParticipanteManagementResponse();
        r.setParticipanteId(p.getId());
        r.setClienteConsumoId(p.getClienteConsumo() != null ? p.getClienteConsumo().getId() : null);
        r.setRole(p.getRole());
        r.setStatus(p.getStatus());
        r.setJoinedAt(p.getJoinedAt());
        r.setRemovedAt(p.getRemovedAt());
        r.setBlockedAt(p.getBlockedAt());
        return r;
    }

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof DevicePrincipal device)) {
            throw new DeviceUnauthorizedException("DevicePrincipal ausente.");
        }
        return device;
    }
}
