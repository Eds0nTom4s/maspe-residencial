package com.restaurante.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Pedido de checkout da Loja Oficial GDSE.
 *
 * <p>O comprador submete os itens do carrinho e o método de pagamento.
 * O motor gere internamente a ordem, pagamento e separação logística.
 */
public class StoreCheckoutRequest {

    /**
     * Itens do carrinho.
     */
    @NotEmpty(message = "A ordem deve ter pelo menos um item")
    @Valid
    private List<StoreCarrinhoItemRequest> itens;

    /**
     * Método de pagamento escolhido pelo comprador.
     *
     * <ul>
     *   <li>{@code WALLET} — débito directo do saldo de sócio, quando disponível no app</li>
     *   <li>{@code APPYPAY_GPO} — pagamento via cartão AppyPay GPO</li>
     *   <li>{@code APPYPAY_REF} — pagamento por referência AppyPay (multibanco)</li>
     * </ul>
     */
    @NotNull(message = "Método de pagamento é obrigatório")
    private MetodoPagamentoLoja metodoPagamento;

    /**
     * Endereço de entrega (opcional no MVP — recolha na sede é o modo padrão).
     */
    private String enderecoEntrega;

    /**
     * Nota adicional do comprador (ex: "entregar em mão").
     */
    private String notas;

    /**
     * Chave de idempotência fornecida pelo app para evitar ordens duplicadas.
     */
    private String idempotencyKey;

    /**
     * Nome do comprador público. Opcional quando há token de sócio válido.
     */
    private String compradorNome;

    /**
     * Telefone do comprador público. Obrigatório quando não há token de sócio.
     */
    private String compradorTelefone;

    @Email(message = "Email do comprador inválido")
    private String compradorEmail;

    // ── Enum de métodos de pagamento da loja ──────────────────────────────────

    public enum MetodoPagamentoLoja {
        /** Débito directo do saldo de sócio, quando disponível no app. */
        WALLET,
        /** Pagamento via cartão AppyPay GPO (débito directo ou crédito). */
        APPYPAY_GPO,
        /** Pagamento por referência AppyPay (multibanco). */
        APPYPAY_REF
    }

    // ── Construtores ──────────────────────────────────────────────────────────

    public StoreCheckoutRequest() {}

    public StoreCheckoutRequest(List<StoreCarrinhoItemRequest> itens,
                                 MetodoPagamentoLoja metodoPagamento,
                                 String enderecoEntrega,
                                 String notas) {
        this.itens = itens;
        this.metodoPagamento = metodoPagamento;
        this.enderecoEntrega = enderecoEntrega;
        this.notas = notas;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public List<StoreCarrinhoItemRequest> getItens() { return itens; }
    public void setItens(List<StoreCarrinhoItemRequest> itens) { this.itens = itens; }

    public MetodoPagamentoLoja getMetodoPagamento() { return metodoPagamento; }
    public void setMetodoPagamento(MetodoPagamentoLoja metodoPagamento) { this.metodoPagamento = metodoPagamento; }

    public String getEnderecoEntrega() { return enderecoEntrega; }
    public void setEnderecoEntrega(String enderecoEntrega) { this.enderecoEntrega = enderecoEntrega; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getCompradorNome() { return compradorNome; }
    public void setCompradorNome(String compradorNome) { this.compradorNome = compradorNome; }

    public String getCompradorTelefone() { return compradorTelefone; }
    public void setCompradorTelefone(String compradorTelefone) { this.compradorTelefone = compradorTelefone; }

    public String getCompradorEmail() { return compradorEmail; }
    public void setCompradorEmail(String compradorEmail) { this.compradorEmail = compradorEmail; }
}
