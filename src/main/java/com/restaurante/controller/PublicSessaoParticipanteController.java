package com.restaurante.controller;

import com.restaurante.consumo.participante.service.SessaoConsumoParticipanteService;
import com.restaurante.dto.request.PublicSessaoParticipanteJoinRequest;
import com.restaurante.dto.request.PublicSessaoParticipanteJoinVerifyRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PublicOtpChallengeResponse;
import com.restaurante.dto.response.PublicSessaoParticipanteJoinVerifyResponse;
import com.restaurante.dto.response.PublicSessaoParticipantesResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/q/{token}/participantes")
@RequiredArgsConstructor
@Tag(name = "QR Público - Participantes", description = "Sessão compartilhada: join por telefone+OTP e listagem mínima de participantes")
public class PublicSessaoParticipanteController {

    private final SessaoConsumoParticipanteService participanteService;

    @PostMapping("/join/request")
    @Operation(summary = "Solicitar OTP para entrar na sessão por QR operacional")
    public ResponseEntity<ApiResponse<PublicOtpChallengeResponse>> requestJoin(
            @PathVariable String token,
            @Valid @RequestBody PublicSessaoParticipanteJoinRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        var result = participanteService.requestJoinByQr(token, request.getTelefone(), request.getNomeExibicao(), ip, ua);

        PublicOtpChallengeResponse resp = new PublicOtpChallengeResponse();
        resp.setChallengeId(result.getChallenge().getId());
        resp.setExpiresAt(result.getChallenge().getExpiresAt());
        resp.setResendAvailableAt(result.getResendAvailableAt());
        resp.setMaskedPhone(result.getMaskedPhone());
        resp.setDebugOtp(result.getDebugOtp());
        return ResponseEntity.ok(ApiResponse.success("OTP solicitado", resp));
    }

    @PostMapping("/join/verify")
    @Operation(summary = "Validar OTP e entrar na sessão como participante")
    public ResponseEntity<ApiResponse<PublicSessaoParticipanteJoinVerifyResponse>> verifyJoin(
            @PathVariable String token,
            @Valid @RequestBody PublicSessaoParticipanteJoinVerifyRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        var r = participanteService.verifyJoinOtp(token, request.getChallengeId(), request.getTelefone(), request.getOtp(), request.getNomeExibicao(), ip, ua);

        PublicSessaoParticipanteJoinVerifyResponse resp = new PublicSessaoParticipanteJoinVerifyResponse();
        resp.setParticipanteId(r.participanteId());
        resp.setClienteConsumoId(r.clienteConsumoId());
        resp.setSessaoConsumoId(r.sessaoConsumoId());
        resp.setRole(r.role());
        resp.setStatus(r.status());
        resp.setTelefoneMascarado(r.telefoneMascarado());
        resp.setJoinedAt(r.joinedAt());
        return ResponseEntity.ok(ApiResponse.success("Participante ativo", resp));
    }

    @GetMapping
    @Operation(summary = "Listar participantes (mínimo) da sessão do QR")
    public ResponseEntity<ApiResponse<PublicSessaoParticipantesResponse>> list(
            @PathVariable String token
    ) {
        var list = participanteService.listByQr(token);
        PublicSessaoParticipantesResponse resp = new PublicSessaoParticipantesResponse();
        resp.setParticipantes(list.stream().map(p -> {
            PublicSessaoParticipantesResponse.Item i = new PublicSessaoParticipantesResponse.Item();
            i.setParticipanteId(p.participanteId());
            i.setNomeExibicao(p.nomeExibicao());
            i.setRole(p.role());
            i.setStatus(p.status());
            i.setJoinedAt(p.joinedAt());
            return i;
        }).toList());
        return ResponseEntity.ok(ApiResponse.success("Participantes", resp));
    }
}

