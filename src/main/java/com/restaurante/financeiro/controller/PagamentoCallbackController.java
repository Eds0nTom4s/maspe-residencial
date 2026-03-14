package com.restaurante.financeiro.controller;

import com.restaurante.financeiro.service.PagamentoCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para callbacks do gateway AppyPay.
 *
 * <p>Endpoint público (sem autenticação JWT) — ação chamada pela AppyPay
 * quando o estado de um pagamento muda.
 *
 * <p>Segurança garantida por validação HMAC-SHA256 no {@link PagamentoCallbackService}.
 * O corpo bruto (íntegra do payload) é passado tal como recebido para garantir
 * que o HMAC é calculado sobre os bytes originais.
 *
 * <p>Retorna sempre 200 OK para evitar retry infinito do gateway,
 * mas callbacks com assinatura inválida são descartados antes de qualquer processamento.
 */
@RestController
@RequestMapping("/api/pagamentos")
@RequiredArgsConstructor
@Slf4j
public class PagamentoCallbackController {

    private final PagamentoCallbackService callbackService;

    /**
     * Recebe e processa callback da AppyPay.
     *
     * @param signature header {@code X-AppyPay-Signature} com HMAC-SHA256 do payload
     * @param rawBody   corpo bruto da requisição (JSON original sem desserialização)
     */
    @PostMapping("/callback")
    public ResponseEntity<Void> processarCallback(
            @RequestHeader(value = "X-AppyPay-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        log.info("Callback AppyPay recebido. Signature presente: {}", signature != null);

        try {
            callbackService.processarCallback(rawBody, signature);
        } catch (Exception e) {
            // Log do erro mas retorna 200 para evitar retry infinito do gateway
            log.error("Erro ao processar callback AppyPay: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }
}
