package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PublicCardapioResponse;
import com.restaurante.dto.request.PublicQrPedidoRequest;
import com.restaurante.dto.response.PublicQrPedidoResponse;
import com.restaurante.dto.response.QrPublicContext;
import com.restaurante.service.PublicQrPedidoService;
import com.restaurante.service.QrCodeOperacionalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/**
 * Controller REST PÚBLICO para leitura de QR Codes operacionais.
 *
 * <p>Não requer autenticação (JWT). O tenant é resolvido exclusivamente pelo token público.
 */
@RestController
@RequestMapping("/public/q")
@RequiredArgsConstructor
@Tag(name = "QR Público", description = "Endpoints públicos (sem autenticação) para resolução de QR operacional e cardápio do tenant")
public class PublicQrController {

    private final QrCodeOperacionalService qrCodeOperacionalService;
    private final PublicQrPedidoService publicQrPedidoService;

    @GetMapping("/{token}")
    @Operation(summary = "Resolver QR operacional por token", description = "Retorna metadados públicos (tenant/instituição/unidade/mesa) a partir do token não enumerável.")
    public ResponseEntity<ApiResponse<QrPublicContext>> resolver(@PathVariable String token) {
        QrPublicContext ctx = qrCodeOperacionalService.resolverPublico(token);
        return ResponseEntity.ok(ApiResponse.success("QR resolvido", ctx));
    }

    @GetMapping("/{token}/cardapio")
    @Operation(summary = "Carregar cardápio público por QR", description = "Retorna categorias e produtos públicos do tenant resolvido pelo token. Não cria pedido.")
    public ResponseEntity<ApiResponse<PublicCardapioResponse>> cardapio(@PathVariable String token) {
        PublicCardapioResponse resp = qrCodeOperacionalService.carregarCardapioPublicoPorQrToken(token);
        return ResponseEntity.ok(ApiResponse.success("Cardápio carregado", resp));
    }

    @PostMapping("/{token}/pedidos")
    @Operation(summary = "Criar pedido público por QR", description = "Cria pedido tenant-safe resolvendo tenant/instituição/unidade/mesa pelo token. Não processa pagamento.")
    public ResponseEntity<ApiResponse<PublicQrPedidoResponse>> criarPedido(
            @PathVariable String token,
            @Valid @RequestBody PublicQrPedidoRequest request
    ) {
        PublicQrPedidoResponse resp = publicQrPedidoService.criarPedidoPublicoPorQrToken(token, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Pedido criado", resp));
    }
}
