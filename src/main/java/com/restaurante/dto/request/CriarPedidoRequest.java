package com.restaurante.dto.request;

import com.restaurante.model.enums.TipoPagamentoPedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO para criação de pedido
 * Cliente cria pedido em uma mesa existente
 *
 * TIPO DE PAGAMENTO:
 * - PRE_PAGO (default): Débito automático do Fundo de Consumo
 * - POS_PAGO: Pagamento posterior (apenas GERENTE/ADMIN)
 */
public class CriarPedidoRequest {

    @NotNull(message = "ID da sessão de consumo é obrigatório")
    private Long sessaoConsumoId;

    @NotEmpty(message = "Pedido deve conter ao menos um item")
    @Valid
    private List<ItemPedidoRequest> itens;

    /**
     * Tipo de pagamento (opcional, default: PRE_PAGO)
     */
    private TipoPagamentoPedido tipoPagamento;

    private String observacoes;

    // ── Constructors ──────────────────────────────────────────────────────────
    public CriarPedidoRequest() {}

    public CriarPedidoRequest(Long sessaoConsumoId, List<ItemPedidoRequest> itens,
                               TipoPagamentoPedido tipoPagamento, String observacoes) {
        this.sessaoConsumoId = sessaoConsumoId;
        this.itens = itens;
        this.tipoPagamento = tipoPagamento;
        this.observacoes = observacoes;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public Long getSessaoConsumoId() { return sessaoConsumoId; }
    public List<ItemPedidoRequest> getItens() { return itens; }
    public TipoPagamentoPedido getTipoPagamento() { return tipoPagamento; }
    public String getObservacoes() { return observacoes; }

    // ── Setters ───────────────────────────────────────────────────────────────
    public void setSessaoConsumoId(Long sessaoConsumoId) { this.sessaoConsumoId = sessaoConsumoId; }
    public void setItens(List<ItemPedidoRequest> itens) { this.itens = itens; }
    public void setTipoPagamento(TipoPagamentoPedido tipoPagamento) { this.tipoPagamento = tipoPagamento; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }

    // ── Builder ───────────────────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long sessaoConsumoId;
        private List<ItemPedidoRequest> itens;
        private TipoPagamentoPedido tipoPagamento;
        private String observacoes;

        public Builder sessaoConsumoId(Long sessaoConsumoId) { this.sessaoConsumoId = sessaoConsumoId; return this; }
        public Builder itens(List<ItemPedidoRequest> itens) { this.itens = itens; return this; }
        public Builder tipoPagamento(TipoPagamentoPedido tipoPagamento) { this.tipoPagamento = tipoPagamento; return this; }
        public Builder observacoes(String observacoes) { this.observacoes = observacoes; return this; }

        public CriarPedidoRequest build() {
            return new CriarPedidoRequest(sessaoConsumoId, itens, tipoPagamento, observacoes);
        }
    }
}
