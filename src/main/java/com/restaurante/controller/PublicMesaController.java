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
     * Busca os dados da mesa a partir do QR Code token ou da referência impressa.
     * GET /api/public/mesa/{codigo}
     */
    @GetMapping("/{codigo}")
    @Operation(summary = "Obter dados da mesa por código", description = "Aceita token do QR Code ou referência impressa na mesa. Não cria sessão.")
    public ResponseEntity<ApiResponse<MesaResponse>> obterMesaPorCodigo(@PathVariable String codigo) {
        MesaResponse mesaResponse = mesaService.buscarPorCodigoPublico(codigo);
        return ResponseEntity.ok(ApiResponse.success("Mesa identificada com sucesso", mesaResponse));
    }
}
