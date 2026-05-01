package com.restaurante.store.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Resposta de uma ordem de compra da Loja Oficial GDSE.
 *
 * <p>Expõe apenas o vocabulário de loja (ordem, itens, pagamento).
 * Campos internos do motor não são visíveis.
 */
public class StoreOrdemResponse {

    private Long id;

    /**
     * Número único da ordem, ex: {@code ORD-20260428-001}.
     * Derivado internamente do número de Pedido do motor.
     */
    private String numero;

    /**
     * Estado operacional da ordem.
     * Valores: CRIADA | EM_SEPARACAO | PRONTA | ENTREGUE | CANCELADA
     */
    private String status;

    /**
     * Estado do pagamento.
     * Valores: PENDENTE | PAGO | AGUARDANDO_CONFIRMACAO | ESTORNADO
     */
    private String statusPagamento;

    private BigDecimal total;

    private String metodoPagamento;

    private List<StoreOrdemItemResponse> itens;

    private LocalDateTime criadaEm;

    private LocalDateTime atualizadaEm;

    /**
     * Referência de pagamento AppyPay (se aplicável).
     */
    private String referenciaPagamento;
    private String entidadePagamento;
    private String paymentUrl;
    private String enderecoEntrega;
    private String notas;

    // ── Item aninhado ────────────────────────────────────────────────────────

    public static class StoreOrdemItemResponse {
        private Long produtoId;
        private String nomeProduto;
        private String variacaoDescricao; // ex: "Tamanho: L | Cor: Vermelho"
        private Integer quantidade;
        private BigDecimal precoUnitario;
        private BigDecimal subtotal;
        private String observacoes;

        public StoreOrdemItemResponse() {}

        public Long getProdutoId() { return produtoId; }
        public void setProdutoId(Long produtoId) { this.produtoId = produtoId; }
        public String getNomeProduto() { return nomeProduto; }
        public void setNomeProduto(String nomeProduto) { this.nomeProduto = nomeProduto; }
        public String getVariacaoDescricao() { return variacaoDescricao; }
        public void setVariacaoDescricao(String variacaoDescricao) { this.variacaoDescricao = variacaoDescricao; }
        public Integer getQuantidade() { return quantidade; }
        public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }
        public BigDecimal getPrecoUnitario() { return precoUnitario; }
        public void setPrecoUnitario(BigDecimal precoUnitario) { this.precoUnitario = precoUnitario; }
        public BigDecimal getSubtotal() { return subtotal; }
        public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
        public String getObservacoes() { return observacoes; }
        public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    }

    // ── Construtores ─────────────────────────────────────────────────────────

    public StoreOrdemResponse() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusPagamento() { return statusPagamento; }
    public void setStatusPagamento(String statusPagamento) { this.statusPagamento = statusPagamento; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getMetodoPagamento() { return metodoPagamento; }
    public void setMetodoPagamento(String metodoPagamento) { this.metodoPagamento = metodoPagamento; }

    public List<StoreOrdemItemResponse> getItens() { return itens; }
    public void setItens(List<StoreOrdemItemResponse> itens) { this.itens = itens; }

    public LocalDateTime getCriadaEm() { return criadaEm; }
    public void setCriadaEm(LocalDateTime criadaEm) { this.criadaEm = criadaEm; }

    public LocalDateTime getAtualizadaEm() { return atualizadaEm; }
    public void setAtualizadaEm(LocalDateTime atualizadaEm) { this.atualizadaEm = atualizadaEm; }

    public String getReferenciaPagamento() { return referenciaPagamento; }
    public void setReferenciaPagamento(String referenciaPagamento) { this.referenciaPagamento = referenciaPagamento; }

    public String getEntidadePagamento() { return entidadePagamento; }
    public void setEntidadePagamento(String entidadePagamento) { this.entidadePagamento = entidadePagamento; }

    public String getPaymentUrl() { return paymentUrl; }
    public void setPaymentUrl(String paymentUrl) { this.paymentUrl = paymentUrl; }

    public String getEnderecoEntrega() { return enderecoEntrega; }
    public void setEnderecoEntrega(String enderecoEntrega) { this.enderecoEntrega = enderecoEntrega; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
}
