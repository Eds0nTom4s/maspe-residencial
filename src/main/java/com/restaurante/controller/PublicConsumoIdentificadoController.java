package com.restaurante.controller;

import com.restaurante.consumo.identificacao.service.ConsumoIdentificadoPublicService;
import com.restaurante.dto.request.PublicIdentificacaoOtpRequest;
import com.restaurante.dto.request.PublicIdentificacaoOtpVerifyRequest;
import com.restaurante.dto.request.PublicRecuperacaoOtpRequest;
import com.restaurante.dto.request.PublicRecuperacaoOtpVerifyRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PublicIdentificacaoOtpVerifyResponse;
import com.restaurante.dto.response.PublicOtpChallengeResponse;
import com.restaurante.dto.response.PublicRecuperacaoOtpVerifyResponse;
import com.restaurante.dto.response.PublicRecuperacaoSessaoResumoResponse;
import com.restaurante.model.enums.OtpPurpose;
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

import java.util.List;

@RestController
@RequestMapping("/public/q/{token}")
@RequiredArgsConstructor
@Tag(name = "QR Público - Consumo Identificado", description = "Identificação leve por telefone+OTP e recuperação de sessão ativa")
public class PublicConsumoIdentificadoController {

    private final ConsumoIdentificadoPublicService consumoIdentificadoPublicService;

    @PostMapping("/identificacao/otp/request")
    @Operation(summary = "Solicitar OTP para identificar sessão (QR)")
    public ResponseEntity<ApiResponse<PublicOtpChallengeResponse>> requestIdentifyOtp(
            @PathVariable String token,
            @Valid @RequestBody PublicIdentificacaoOtpRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        OtpPurpose purpose = request.getPurpose() != null ? request.getPurpose() : OtpPurpose.IDENTIFICAR_SESSAO;

        var result = consumoIdentificadoPublicService.requestOtpForIdentify(token, request.getTelefone(), purpose, ip, ua);
        PublicOtpChallengeResponse resp = new PublicOtpChallengeResponse();
        resp.setChallengeId(result.getChallenge().getId());
        resp.setExpiresAt(result.getChallenge().getExpiresAt());
        resp.setResendAvailableAt(result.getResendAvailableAt());
        resp.setMaskedPhone(result.getMaskedPhone());
        resp.setDebugOtp(result.getDebugOtp());
        return ResponseEntity.ok(ApiResponse.success("OTP solicitado", resp));
    }

    @PostMapping("/identificacao/otp/verify")
    @Operation(summary = "Validar OTP e identificar sessão (QR)")
    public ResponseEntity<ApiResponse<PublicIdentificacaoOtpVerifyResponse>> verifyIdentifyOtp(
            @PathVariable String token,
            @Valid @RequestBody PublicIdentificacaoOtpVerifyRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var result = consumoIdentificadoPublicService.verifyOtpAndIdentifySessao(
                token,
                request.getChallengeId(),
                request.getTelefone(),
                request.getOtp(),
                ip,
                ua
        );

        PublicIdentificacaoOtpVerifyResponse resp = new PublicIdentificacaoOtpVerifyResponse();
        resp.setClienteConsumoId(result.clienteConsumoId());
        resp.setSessaoConsumoId(result.sessaoConsumoId());
        resp.setTelefoneMascarado(result.telefoneMascarado());
        resp.setIdentificacaoStatus(result.identificacaoStatus());

        return ResponseEntity.ok(ApiResponse.success("Sessão identificada", resp));
    }

    @PostMapping("/recuperar/otp/request")
    @Operation(summary = "Solicitar OTP para recuperar sessão ativa por telefone (contexto QR)")
    public ResponseEntity<ApiResponse<PublicOtpChallengeResponse>> requestRecoveryOtp(
            @PathVariable String token,
            @Valid @RequestBody PublicRecuperacaoOtpRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        var result = consumoIdentificadoPublicService.requestOtpForRecovery(token, request.getTelefone(), ip, ua);

        PublicOtpChallengeResponse resp = new PublicOtpChallengeResponse();
        resp.setChallengeId(result.getChallenge().getId());
        resp.setExpiresAt(result.getChallenge().getExpiresAt());
        resp.setResendAvailableAt(result.getResendAvailableAt());
        resp.setMaskedPhone(result.getMaskedPhone());
        resp.setDebugOtp(result.getDebugOtp());
        return ResponseEntity.ok(ApiResponse.success("OTP solicitado", resp));
    }

    @PostMapping("/recuperar/otp/verify")
    @Operation(summary = "Validar OTP e listar sessões ativas do telefone (contexto QR)")
    public ResponseEntity<ApiResponse<PublicRecuperacaoOtpVerifyResponse>> verifyRecoveryOtp(
            @PathVariable String token,
            @Valid @RequestBody PublicRecuperacaoOtpVerifyRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        List<ConsumoIdentificadoPublicService.RecoveredSessaoResumo> list = consumoIdentificadoPublicService.verifyOtpAndRecoverSessions(
                token,
                request.getChallengeId(),
                request.getTelefone(),
                request.getOtp(),
                ip,
                ua
        );

        PublicRecuperacaoOtpVerifyResponse resp = new PublicRecuperacaoOtpVerifyResponse();
        resp.setSessoesAtivas(list.stream()
                .map(s -> new PublicRecuperacaoSessaoResumoResponse(s.sessaoId(), s.codigoConsumo(), s.abertaEm(), s.unidade(), s.saldoFundo(), s.status()))
                .toList());

        return ResponseEntity.ok(ApiResponse.success("Sessões ativas", resp));
    }
}

