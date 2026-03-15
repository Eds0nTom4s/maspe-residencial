package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.MesaResponse;
import com.restaurante.service.MesaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST PÚBLICO para operações iniciais com a Mesa
 * Este controller não requer Autenticação (JWT)
 */
@RestController
@RequestMapping("/public/mesa")
@RequiredArgsConstructor
@Tag(name = "Mesa Pública", description = "Endpoints de acesso público (sem autenticação) para leitura do QR Code da mesa")
public class PublicMesaController {

    private final MesaService mesaService;

    /**
     * Busca os dados da mesa a partir do seu QR Code token
     * GET /api/public/mesa/{qrToken}
     */
    @GetMapping("/{qrToken}")
    @Operation(summary = "Obter dados da mesa por QR Token", description = "Retorna os detalhes públicos da mesa ao ser escaneada. Não cria sessão.")
    public ResponseEntity<ApiResponse<MesaResponse>> obterMesaPorQrToken(@PathVariable String qrToken) {
        MesaResponse mesaResponse = mesaService.buscarPorQrCode(qrToken);
        return ResponseEntity.ok(ApiResponse.success("Mesa identificada com sucesso", mesaResponse));
    }
}
