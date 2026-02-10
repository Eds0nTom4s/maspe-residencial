package com.restaurante.controller;

import com.google.zxing.WriterException;
import com.restaurante.dto.request.GerarQrCodeRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.QrCodeResponse;
import com.restaurante.dto.response.QrCodeValidacaoResponse;
import com.restaurante.service.QrCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * Controller REST para operações com QR Code
 */
@RestController
@RequestMapping("/qrcode")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "QR Code", description = "Endpoints para gestão de QR Codes seguros")
public class QrCodeController {

    private final QrCodeService qrCodeService;

    /**
     * Gera novo QR Code
     * POST /api/qrcode
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    @Operation(summary = "Gerar QR Code", description = "Gera um novo QR Code com expiração")
    public ResponseEntity<ApiResponse<QrCodeResponse>> gerarQrCode(@Valid @RequestBody GerarQrCodeRequest request) {
        log.info("Requisição para gerar QR Code tipo: {}", request.getTipo());
        
        QrCodeResponse qrCode = qrCodeService.gerarQrCode(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("QR Code gerado com sucesso", qrCode));
    }

    /**
     * Valida QR Code por token
     * GET /api/qrcode/validar/{token}
     */
    @GetMapping("/validar/{token}")
    @Operation(summary = "Validar QR Code", description = "Valida se um QR Code está ativo e não expirado")
    public ResponseEntity<ApiResponse<QrCodeValidacaoResponse>> validarQrCode(@PathVariable String token) {
        log.info("Validando QR Code: {}", token);
        
        QrCodeValidacaoResponse validacao = qrCodeService.validarQrCode(token);
        return ResponseEntity.ok(ApiResponse.success("Validação realizada", validacao));
    }

    /**
     * Usa QR Code (marca como usado para uso único)
     * POST /api/qrcode/usar/{token}
     */
    @PostMapping("/usar/{token}")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'COZINHA', 'GERENTE', 'ADMIN')")
    @Operation(summary = "Usar QR Code", description = "Marca QR Code como usado (para uso único)")
    public ResponseEntity<ApiResponse<QrCodeResponse>> usarQrCode(@PathVariable String token) {
        log.info("Usando QR Code: {}", token);
        
        QrCodeResponse qrCode = qrCodeService.usarQrCode(token);
        return ResponseEntity.ok(ApiResponse.success("QR Code usado com sucesso", qrCode));
    }

    /**
     * Renova QR Code (apenas tipo MESA)
     * POST /api/qrcode/renovar/{token}
     */
    @PostMapping("/renovar/{token}")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    @Operation(summary = "Renovar QR Code", description = "Renova validade de QR Code de mesa")
    public ResponseEntity<ApiResponse<QrCodeResponse>> renovarQrCode(@PathVariable String token) {
        log.info("Renovando QR Code: {}", token);
        
        QrCodeResponse qrCode = qrCodeService.renovarQrCode(token);
        return ResponseEntity.ok(ApiResponse.success("QR Code renovado com sucesso", qrCode));
    }

    /**
     * Cancela QR Code
     * DELETE /api/qrcode/{token}
     */
    @DeleteMapping("/{token}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Cancelar QR Code", description = "Cancela um QR Code tornando-o inválido")
    public ResponseEntity<ApiResponse<Void>> cancelarQrCode(@PathVariable String token) {
        log.info("Cancelando QR Code: {}", token);
        
        qrCodeService.cancelarQrCode(token);
        return ResponseEntity.ok(ApiResponse.success("QR Code cancelado com sucesso", null));
    }

    /**
     * Busca QR Codes por Unidade de Consumo
     * GET /api/qrcode/unidade-consumo/{id}
     */
    @GetMapping("/unidade-consumo/{id}")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    @Operation(summary = "QR Codes por Unidade", description = "Busca QR Codes ativos de uma unidade de consumo")
    public ResponseEntity<ApiResponse<List<QrCodeResponse>>> buscarPorUnidadeDeConsumo(@PathVariable Long id) {
        log.info("Buscando QR Codes da unidade de consumo: {}", id);
        
        List<QrCodeResponse> qrCodes = qrCodeService.buscarPorUnidadeDeConsumo(id);
        return ResponseEntity.ok(ApiResponse.success("QR Codes encontrados", qrCodes));
    }

    /**
     * Gera imagem PNG do QR Code
     * GET /api/qrcode/imagem/{token}
     */
    @GetMapping("/imagem/{token}")
    @Operation(summary = "Imagem do QR Code", description = "Gera imagem PNG do QR Code (300x300)")
    public ResponseEntity<byte[]> gerarImagemQrCode(@PathVariable String token) {
        log.info("Gerando imagem QR Code: {}", token);
        
        try {
            byte[] imagem = qrCodeService.gerarImagemQrCode(token);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentLength(imagem.length);
            headers.set("Content-Disposition", "inline; filename=\"qrcode-" + token + ".png\"");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(imagem);
                    
        } catch (WriterException | IOException e) {
            log.error("Erro ao gerar imagem QR Code: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Gera imagem PNG do QR Code para impressão (alta resolução)
     * GET /api/qrcode/imagem/{token}/print
     */
    @GetMapping("/imagem/{token}/print")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    @Operation(summary = "Imagem para impressão", description = "Gera imagem PNG do QR Code em alta resolução (500x500)")
    public ResponseEntity<byte[]> gerarImagemQrCodeParaImpressao(@PathVariable String token) {
        log.info("Gerando imagem QR Code para impressão: {}", token);
        
        try {
            byte[] imagem = qrCodeService.gerarImagemQrCodeParaImpressao(token);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentLength(imagem.length);
            headers.set("Content-Disposition", "attachment; filename=\"qrcode-print-" + token + ".png\"");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(imagem);
                    
        } catch (WriterException | IOException e) {
            log.error("Erro ao gerar imagem QR Code para impressão: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
