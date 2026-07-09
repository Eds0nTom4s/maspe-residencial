package com.restaurante.controller;

import com.restaurante.dto.request.CriarCarregamentoFundoRequest;
import com.restaurante.dto.request.PublicCriarOrdemPagamentoManualPedidoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.GerirConsumoOptionsResponse;
import com.restaurante.dto.response.OrdemPagamentoResponse;
import com.restaurante.dto.response.SessaoConsumoResponse;
import com.restaurante.service.ConsumoPublicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/q")
@RequiredArgsConstructor
@Tag(name = "QR Público - Consumo", description = "Fluxo público de consumo físico (anónimo) + ordens de pagamento manual CASH/TPA")
public class PublicQrConsumoController {

    private final ConsumoPublicService consumoPublicService;

    @GetMapping("/{token}/consumos/opcoes")
    @Operation(summary = "Opções de gerir consumo por QR")
    public ResponseEntity<ApiResponse<GerirConsumoOptionsResponse>> opcoes(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success("Opções", consumoPublicService.opcoes(token)));
    }

    @PostMapping("/{token}/consumos/anonimo")
    @Operation(summary = "Criar consumo anónimo (SessaoConsumo) por QR operacional")
    public ResponseEntity<ApiResponse<SessaoConsumoResponse>> criarAnonimo(@PathVariable String token) {
        SessaoConsumoResponse resp = consumoPublicService.criarConsumoAnonimo(token);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Consumo criado", resp));
    }

    @PostMapping("/{token}/consumos/{codigoConsumo}/carregamentos")
    @Operation(summary = "Criar ordem de carregamento de FundoConsumo (manual CASH/TPA)")
    public ResponseEntity<ApiResponse<OrdemPagamentoResponse>> criarCarregamento(
            @PathVariable String token,
            @PathVariable String codigoConsumo,
            @Valid @RequestBody CriarCarregamentoFundoRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        OrdemPagamentoResponse resp = consumoPublicService.criarOrdemCarregamentoFundo(token, codigoConsumo, request, ip, ua);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Ordem criada", resp));
    }

    @PostMapping("/{token}/pedidos/{pedidoId}/ordens-pagamento-manual")
    @Operation(summary = "Criar ordem de pagamento manual (CASH/TPA) para um pedido")
    public ResponseEntity<ApiResponse<OrdemPagamentoResponse>> criarOrdemPedidoManual(
            @PathVariable String token,
            @PathVariable Long pedidoId,
            @Valid @RequestBody PublicCriarOrdemPagamentoManualPedidoRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        OrdemPagamentoResponse resp = consumoPublicService.criarOrdemPagamentoManualPedido(token, pedidoId, request, ip, ua);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Ordem criada", resp));
    }
}

