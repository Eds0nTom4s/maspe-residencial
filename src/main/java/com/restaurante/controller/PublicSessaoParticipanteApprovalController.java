package com.restaurante.controller;

import com.restaurante.consumo.participante.service.SessaoConsumoParticipanteService;
import com.restaurante.consumo.identificacao.service.TelefoneNormalizerService;
import com.restaurante.dto.request.PublicInviteAcceptRequest;
import com.restaurante.dto.request.PublicOwnerAuthActionRequest;
import com.restaurante.dto.request.PublicOwnerAuthOtpRequest;
import com.restaurante.dto.request.PublicOwnerInviteRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PublicOtpChallengeResponse;
import com.restaurante.dto.response.PublicSessaoParticipanteJoinVerifyResponse;
import com.restaurante.dto.response.PublicSessaoParticipantesPendingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/q/{token}")
@RequiredArgsConstructor
@Tag(name = "QR Público - Participantes (Owner Approval)", description = "Aprovação/convite de participantes pelo OWNER (Prompt 41.2)")
public class PublicSessaoParticipanteApprovalController {

    private final SessaoConsumoParticipanteService participanteService;
    private final TelefoneNormalizerService telefoneNormalizerService;

    @PostMapping("/owner-auth/otp/request")
    @Operation(summary = "Solicitar OTP de autorização do OWNER para aprovar/rejeitar participantes")
    public ResponseEntity<ApiResponse<PublicOtpChallengeResponse>> requestOwnerAuthOtp(
            @PathVariable String token,
            @Valid @RequestBody PublicOwnerAuthOtpRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var result = participanteService.requestOwnerAuthOtp(token, request.getTelefone(), ip, ua);
        PublicOtpChallengeResponse resp = new PublicOtpChallengeResponse();
        resp.setChallengeId(result.getChallenge().getId());
        resp.setExpiresAt(result.getChallenge().getExpiresAt());
        resp.setResendAvailableAt(result.getResendAvailableAt());
        resp.setMaskedPhone(result.getMaskedPhone());
        resp.setDebugOtp(result.getDebugOtp());
        return ResponseEntity.ok(ApiResponse.success("OTP solicitado", resp));
    }

    @PostMapping("/participantes/pending")
    @Operation(summary = "Listar participantes pendentes (PENDING_APPROVAL) — exige auth OTP do OWNER")
    public ResponseEntity<ApiResponse<PublicSessaoParticipantesPendingResponse>> listPending(
            @PathVariable String token,
            @Valid @RequestBody PublicOwnerAuthActionRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var list = participanteService.listPendingForOwner(token, request.getOwnerChallengeId(), request.getOwnerTelefone(), request.getOtp(), ip, ua);
        PublicSessaoParticipantesPendingResponse resp = new PublicSessaoParticipantesPendingResponse();
        resp.setParticipantes(list.stream().map(p -> {
            PublicSessaoParticipantesPendingResponse.Item i = new PublicSessaoParticipantesPendingResponse.Item();
            i.setParticipanteId(p.getId());
            i.setNomeExibicao(p.getNomeExibicao());
            i.setStatus(p.getStatus());
            i.setJoinedAt(p.getJoinedAt());
            i.setTelefoneMascarado(telefoneNormalizerService.mask(p.getTelefoneNormalizado()));
            i.setExpiresAt(p.getExpiresAt());
            i.setResendCount(p.getResendCount());
            i.setLastResendAt(p.getLastResendAt());
            i.setCanResend(participanteService.canResendInviteNow(p));
            return i;
        }).toList());
        return ResponseEntity.ok(ApiResponse.success("Pendentes", resp));
    }

    @PostMapping("/participantes/{participanteId}/approve")
    @Operation(summary = "Aprovar participante pendente — exige auth OTP do OWNER")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable String token,
            @PathVariable Long participanteId,
            @Valid @RequestBody PublicOwnerAuthActionRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        participanteService.approveByOwner(token, participanteId, request.getOwnerChallengeId(), request.getOwnerTelefone(), request.getOtp(), request.getReason(), ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Aprovado", null));
    }

    @PostMapping("/participantes/{participanteId}/reject")
    @Operation(summary = "Rejeitar participante pendente — exige auth OTP do OWNER")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable String token,
            @PathVariable Long participanteId,
            @Valid @RequestBody PublicOwnerAuthActionRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        participanteService.rejectByOwner(token, participanteId, request.getOwnerChallengeId(), request.getOwnerTelefone(), request.getOtp(), request.getReason(), ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Rejeitado", null));
    }

    @PostMapping("/participantes/invite")
    @Operation(summary = "Convidar participante por telefone — exige auth OTP do OWNER")
    public ResponseEntity<ApiResponse<PublicOtpChallengeResponse>> invite(
            @PathVariable String token,
            @Valid @RequestBody PublicOwnerInviteRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var result = participanteService.inviteByOwner(
                token,
                request.getOwnerChallengeId(),
                request.getOwnerTelefone(),
                request.getOwnerOtp(),
                request.getTelefoneConvidado(),
                request.getNomeExibicao(),
                ip,
                ua
        );

        PublicOtpChallengeResponse resp = new PublicOtpChallengeResponse();
        resp.setChallengeId(result.getChallenge().getId());
        resp.setExpiresAt(result.getChallenge().getExpiresAt());
        resp.setResendAvailableAt(result.getResendAvailableAt());
        resp.setMaskedPhone(result.getMaskedPhone());
        resp.setDebugOtp(result.getDebugOtp());
        return ResponseEntity.ok(ApiResponse.success("Convite enviado (OTP)", resp));
    }

    @PostMapping("/participantes/{participanteId}/cancel")
    @Operation(summary = "Cancelar convite/pendência (INVITED/PENDING_APPROVAL) pelo OWNER — exige auth OTP do OWNER")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable String token,
            @PathVariable Long participanteId,
            @Valid @RequestBody PublicOwnerAuthActionRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        participanteService.cancelByOwner(token, participanteId, request.getOwnerChallengeId(), request.getOwnerTelefone(), request.getOtp(), request.getReason(), ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Cancelado", null));
    }

    @PostMapping("/participantes/{participanteId}/resend-invite")
    @Operation(summary = "Reenviar convite (OTP) pelo OWNER — exige auth OTP do OWNER")
    public ResponseEntity<ApiResponse<PublicOtpChallengeResponse>> resendInvite(
            @PathVariable String token,
            @PathVariable Long participanteId,
            @Valid @RequestBody PublicOwnerAuthActionRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        var result = participanteService.resendInviteByOwner(token, participanteId, request.getOwnerChallengeId(), request.getOwnerTelefone(), request.getOtp(), ip, ua);
        PublicOtpChallengeResponse resp = new PublicOtpChallengeResponse();
        resp.setChallengeId(result.getChallenge().getId());
        resp.setExpiresAt(result.getChallenge().getExpiresAt());
        resp.setResendAvailableAt(result.getResendAvailableAt());
        resp.setMaskedPhone(result.getMaskedPhone());
        resp.setDebugOtp(result.getDebugOtp());
        return ResponseEntity.ok(ApiResponse.success("Convite reenviado", resp));
    }

    @PostMapping("/participantes/invite/accept")
    @Operation(summary = "Aceitar convite por OTP")
    public ResponseEntity<ApiResponse<PublicSessaoParticipanteJoinVerifyResponse>> acceptInvite(
            @PathVariable String token,
            @Valid @RequestBody PublicInviteAcceptRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var r = participanteService.acceptInvite(token, request.getChallengeId(), request.getTelefone(), request.getOtp(), request.getNomeExibicao(), ip, ua);
        PublicSessaoParticipanteJoinVerifyResponse resp = new PublicSessaoParticipanteJoinVerifyResponse();
        resp.setParticipanteId(r.participanteId());
        resp.setClienteConsumoId(r.clienteConsumoId());
        resp.setSessaoConsumoId(r.sessaoConsumoId());
        resp.setRole(r.role());
        resp.setStatus(r.status());
        resp.setTelefoneMascarado(r.telefoneMascarado());
        resp.setJoinedAt(r.joinedAt());
        return ResponseEntity.ok(ApiResponse.success("Convite aceito", resp));
    }
}
