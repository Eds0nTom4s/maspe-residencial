package com.restaurante.store.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.store.dto.StoreCheckoutRequest;
import com.restaurante.store.dto.StoreOrderDTO;
import com.restaurante.store.dto.StoreOrderTrackingDTO;
import com.restaurante.store.service.StoreCheckoutService;
import com.restaurante.store.service.StoreOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/store/ordens")
public class StoreOrderController {

    private final StoreCheckoutService checkoutService;
    private final StoreOrderService orderService;

    public StoreOrderController(StoreCheckoutService checkoutService, StoreOrderService orderService) {
        this.checkoutService = checkoutService;
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StoreOrderDTO>> createOrder(@Valid @RequestBody StoreCheckoutRequest request,
                                                                  HttpServletRequest httpRequest) {
        StoreOrderDTO order = checkoutService.checkout(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ordem criada com sucesso", order));
    }

    @GetMapping("/minhas")
    public ResponseEntity<ApiResponse<List<StoreOrderDTO>>> listMyOrders(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Ordens do comprador listadas", orderService.listMyOrders(request)));
    }

    @GetMapping("/rastreio")
    public ResponseEntity<ApiResponse<StoreOrderTrackingDTO>> trackPublicOrder(@RequestParam String numero,
                                                                               @RequestParam String telefone) {
        return ResponseEntity.ok(ApiResponse.success("Rastreio da ordem encontrado",
                orderService.trackPublicOrder(numero, telefone)));
    }

    @GetMapping("/{ordemId}")
    public ResponseEntity<ApiResponse<StoreOrderDTO>> getOrder(@PathVariable Long ordemId,
                                                               HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Ordem encontrada", orderService.getOrder(ordemId, request)));
    }

    @GetMapping("/{ordemId}/rastreio")
    public ResponseEntity<ApiResponse<StoreOrderTrackingDTO>> trackAuthenticatedOrder(@PathVariable Long ordemId,
                                                                                      HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Rastreio da ordem encontrado",
                orderService.trackAuthenticatedOrder(ordemId, request)));
    }

    @PutMapping("/{ordemId}/cancelar")
    public ResponseEntity<ApiResponse<StoreOrderDTO>> cancelOrder(@PathVariable Long ordemId,
                                                                  HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Ordem cancelada", orderService.cancelUnpaidOrder(ordemId, request)));
    }
}
