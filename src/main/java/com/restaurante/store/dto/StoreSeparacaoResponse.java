package com.restaurante.store.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Visão do operador de loja para gestão de separação (picking).
 *
 * <p>Visão pública da etapa de separação com vocabulário de loja.
 */
public class StoreSeparacaoResponse {

    /**
     * ID interno da separação.
     */
    private Long subOrdemId;

    /**
     * Número legível da ordem pai, ex: {@code ORD-20260428-001}.
     */
    private String numeroOrdem;

    /**
     * Estado actual da separação.
     * Valores: PENDENTE | EM_SEPARACAO | PRONTO | ENTREGUE | CANCELADO
     */
    private String status;

    /**
     * Nome do comprador que fez a ordem (para facilitar entrega).
     */
    private String compradorNome;

    /**
     * Telefone do comprador (para contacto).
     */
    private String compradorTelefone;

    /**
     * Itens a separar nesta sub-ordem.
     */
    private List<SeparacaoItemResponse> itens;

    private LocalDateTime criadaEm;

    // ── Item aninhado ────────────────────────────────────────────────────────

    public static class SeparacaoItemResponse {
        private String nomeProduto;
        private String variacaoDescricao;
        private Integer quantidade;
        private String observacoes;

        public SeparacaoItemResponse() {}

        public String getNomeProduto() { return nomeProduto; }
        public void setNomeProduto(String nomeProduto) { this.nomeProduto = nomeProduto; }
        public String getVariacaoDescricao() { return variacaoDescricao; }
        public void setVariacaoDescricao(String variacaoDescricao) { this.variacaoDescricao = variacaoDescricao; }
        public Integer getQuantidade() { return quantidade; }
        public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }
        public String getObservacoes() { return observacoes; }
        public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    }

    // ── Construtores ─────────────────────────────────────────────────────────

    public StoreSeparacaoResponse() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getSubOrdemId() { return subOrdemId; }
    public void setSubOrdemId(Long subOrdemId) { this.subOrdemId = subOrdemId; }

    public String getNumeroOrdem() { return numeroOrdem; }
    public void setNumeroOrdem(String numeroOrdem) { this.numeroOrdem = numeroOrdem; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCompradorNome() { return compradorNome; }
    public void setCompradorNome(String compradorNome) { this.compradorNome = compradorNome; }

    public String getCompradorTelefone() { return compradorTelefone; }
    public void setCompradorTelefone(String compradorTelefone) { this.compradorTelefone = compradorTelefone; }

    public List<SeparacaoItemResponse> getItens() { return itens; }
    public void setItens(List<SeparacaoItemResponse> itens) { this.itens = itens; }

    public LocalDateTime getCriadaEm() { return criadaEm; }
    public void setCriadaEm(LocalDateTime criadaEm) { this.criadaEm = criadaEm; }
}
