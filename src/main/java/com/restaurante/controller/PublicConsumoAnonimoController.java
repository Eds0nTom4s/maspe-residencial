package com.restaurante.controller;

import com.restaurante.dto.request.CriarPedidoRequest;
import com.restaurante.dto.request.IniciarPagamentoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PagamentoGatewayResponse;
import com.restaurante.dto.response.PedidoResponse;
import com.restaurante.dto.response.SessaoConsumoResponse;
import com.restaurante.financeiro.service.PagamentoGatewayService;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.service.PedidoService;
import com.restaurante.service.SessaoConsumoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/public/consumo-anonimo")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Consumo Anónimo", description = "Fluxo público de consumo por QR de mesa")
public class PublicConsumoAnonimoController {

    private final SessaoConsumoService sessaoConsumoService;
    private final PagamentoGatewayService pagamentoGatewayService;
    private final PedidoService pedidoService;

    @PostMapping("/sessoes/qr/{tokenMesa}")
    @Operation(summary = "Iniciar sessão anónima por QR da mesa")
    public ResponseEntity<ApiResponse<SessaoConsumoResponse>> iniciarSessaoAnonima(@PathVariable String tokenMesa) {
        SessaoConsumoResponse response = sessaoConsumoService.iniciarSessaoAnonima(tokenMesa);
        return ResponseEntity.ok(ApiResponse.success("Sessão anónima iniciada", response));
    }

    @GetMapping("/sessoes/{qrCodeSessao}")
    @Operation(summary = "Consultar sessão anónima por token da sessão")
    public ResponseEntity<ApiResponse<SessaoConsumoResponse>> consultarSessao(@PathVariable String qrCodeSessao) {
        SessaoConsumoResponse response = sessaoConsumoService.buscarSessaoAnonimaPorToken(qrCodeSessao);
        return ResponseEntity.ok(ApiResponse.success("Sessão anónima encontrada", response));
    }

    @PostMapping("/pagamentos/recarregar")
    @Operation(summary = "Recarregar fundo anónimo via AppyPay")
    public ResponseEntity<ApiResponse<PagamentoGatewayResponse>> recarregarFundoAnonimo(
            @Valid @RequestBody IniciarPagamentoRequest request,
            HttpServletRequest httpRequest) {

        log.info("Recarga pública anónima: fundoId={}, valor={}, metodo={}",
                request.getFundoId(), request.getValor(), request.getMetodo());

        Pagamento pagamento = pagamentoGatewayService.criarPagamentoRecargaFundo(
                request.getFundoId(),
                request.getValor(),
                request.getMetodo(),
                "ANONIMO",
                "ANONIMO",
                httpRequest.getRemoteAddr(),
                null);

        String mensagem = pagamento.isConfirmado()
                ? "Pagamento confirmado. Saldo creditado."
                : "Referência gerada. Entidade: " + pagamento.getEntidade()
                + " | Ref: " + pagamento.getReferencia()
                + " | Valor: " + com.restaurante.util.MoneyFormatter.format(pagamento.getAmount());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(mensagem, toPagamentoResponse(pagamento)));
    }

    @PostMapping("/pedidos/{qrCodeSessao}")
    @Operation(summary = "Criar pedido anónimo usando fundo pré-pago da sessão")
    public ResponseEntity<ApiResponse<PedidoResponse>> criarPedidoAnonimo(
            @PathVariable String qrCodeSessao,
            @Valid @RequestBody CriarPedidoRequest request) {

        PedidoResponse pedido = pedidoService.criarPedidoAnonimo(request, qrCodeSessao);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Pedido anónimo enviado para a cozinha", pedido));
    }

    @GetMapping("/pedidos/{qrCodeSessao}")
    @Operation(summary = "Listar pedidos da sessão anónima")
    public ResponseEntity<ApiResponse<List<PedidoResponse>>> listarPedidosAnonimos(@PathVariable String qrCodeSessao) {
        List<PedidoResponse> pedidos = pedidoService.listarPedidosPorSessaoAnonima(qrCodeSessao);
        return ResponseEntity.ok(ApiResponse.success("Pedidos anónimos encontrados", pedidos));
    }

    private PagamentoGatewayResponse toPagamentoResponse(Pagamento p) {
        return PagamentoGatewayResponse.builder()
                .id(p.getId())
                .fundoConsumoId(p.getFundoConsumo() != null ? p.getFundoConsumo().getId() : null)
                .pedidoId(p.getPedido() != null ? p.getPedido().getId() : null)
                .tipoPagamento(p.getTipoPagamento())
                .metodo(p.getMetodo())
                .amount(p.getAmount())
                .status(p.getStatus())
                .externalReference(p.getExternalReference())
                .entidade(p.getEntidade())
                .referencia(p.getReferencia())
                .confirmedAt(p.getConfirmedAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
