package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PublicCardapioResponse;
import com.restaurante.dto.request.PublicQrPedidoRequest;
import com.restaurante.dto.request.PublicQrPagamentoRequest;
import com.restaurante.dto.response.PublicQrPedidoResponse;
import com.restaurante.dto.response.PublicQrPagamentoResponse;
import com.restaurante.dto.response.QrPublicContext;
import com.restaurante.dto.response.AvailablePaymentMethodResponse;
import com.restaurante.financeiro.paymentmethod.service.PaymentMethodPolicyResolutionService;
import com.restaurante.model.enums.PaymentDestination;
import com.restaurante.service.PublicQrPagamentoService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

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
    private final PublicQrPagamentoService publicQrPagamentoService;
    private final PaymentMethodPolicyResolutionService policyResolutionService;

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

    @GetMapping("/{token}/payment-methods")
    @Operation(summary = "Listar métodos de pagamento disponíveis (QR público)", description = "Retorna métodos ativos e habilitados para QR (tenant-aware), filtrando por destino (PEDIDO/FUNDO_CONSUMO).")
    public ResponseEntity<ApiResponse<java.util.List<AvailablePaymentMethodResponse>>> paymentMethods(
            @PathVariable String token,
            @RequestParam PaymentDestination destination
    ) {
        QrPublicContext ctx = qrCodeOperacionalService.resolverPublico(token);
        var methods = policyResolutionService.listEffectiveForQr(ctx.getTenantId(), ctx.getUnidadeAtendimentoId(), destination);

        return ResponseEntity.ok(ApiResponse.success("Métodos de pagamento disponíveis", methods));
    }

    @PostMapping("/{token}/pedidos")
    @Operation(summary = "Criar pedido público por QR", description = "Cria pedido tenant-safe resolvendo tenant/instituição/unidade/mesa pelo token. Não processa pagamento.")
    public ResponseEntity<ApiResponse<PublicQrPedidoResponse>> criarPedido(
            @PathVariable String token,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PublicQrPedidoRequest request
    ) {
        PublicQrPedidoResponse resp = publicQrPedidoService.criarPedidoPublicoPorQrToken(token, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Pedido criado", resp));
    }

    @PostMapping("/{token}/pedidos/{pedidoId}/pagamentos")
    @Operation(summary = "Iniciar pagamento de pedido por QR", description = "Inicia pagamento digital para um pedido já criado no fluxo público por QR. Não confirma pagamento nesta fase.")
    public ResponseEntity<ApiResponse<PublicQrPagamentoResponse>> iniciarPagamento(
            @PathVariable String token,
            @PathVariable Long pedidoId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PublicQrPagamentoRequest request
    ) {
        PublicQrPagamentoResponse resp = publicQrPagamentoService.iniciarPagamentoPedidoPorQr(token, pedidoId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Pagamento iniciado", resp));
    }
}
