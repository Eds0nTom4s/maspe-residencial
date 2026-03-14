package com.restaurante.model.entity;

import com.restaurante.model.enums.TipoCozinha;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity Cozinha
 * 
 * Representa um recurso operacional responsável pela preparação
 * Exemplos: Cozinha Central, Bar, Confeitaria, Pizzaria
 * 
 * Responsabilidades:
 * - Assumir SubPedidos
 * - Alterar estados de preparo
 * - Imprimir tickets ao marcar como PRONTO
 */
@Entity
@Table(name = "cozinhas", indexes = {
    @Index(name = "idx_cozinha_tipo", columnList = "tipo"),
    @Index(name = "idx_cozinha_ativa", columnList = "ativa")
})
public class Cozinha extends BaseEntity {

    @NotBlank(message = "Nome da cozinha é obrigatório")
    @Column(nullable = false, length = 100)
    private String nome;

    @NotNull(message = "Tipo da cozinha é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoCozinha tipo;

    @Column(name = "ativa", nullable = false)
    private Boolean ativa = true;

    @Column(length = 500)
    private String descricao;

    /**
     * Identificador da impressora associada
     * Usado para roteamento de tickets de impressão
     */
    @Column(name = "impressora_id", length = 50)
    private String impressoraId;

    /**
     * Relacionamento com Unidades de Atendimento
     * Lado inverso do relacionamento ManyToMany
     */
    @ManyToMany(mappedBy = "cozinhas")
    private List<UnidadeAtendimento> unidadesAtendimento = new ArrayList<>();

    /**
     * Relacionamento com SubPedidos desta cozinha
     */
    @OneToMany(mappedBy = "cozinha", fetch = FetchType.LAZY)
    private List<SubPedido> subPedidos = new ArrayList<>();

    /**
     * Verifica se a cozinha está operacional
     */
    public boolean isOperacional() {
        return ativa;
    }

    public String getNome() {
        return this.nome;
    }

    public List<UnidadeAtendimento> getUnidadesAtendimento() {
        return this.unidadesAtendimento;
    }

    // --- Construtores ---
    public Cozinha() {}

    public Cozinha(String nome, TipoCozinha tipo, Boolean ativa, String descricao,
                   String impressoraId, List<UnidadeAtendimento> unidadesAtendimento,
                   List<SubPedido> subPedidos) {
        this.nome = nome;
        this.tipo = tipo;
        this.ativa = ativa != null ? ativa : true;
        this.descricao = descricao;
        this.impressoraId = impressoraId;
        this.unidadesAtendimento = unidadesAtendimento != null ? unidadesAtendimento : new ArrayList<>();
        this.subPedidos = subPedidos != null ? subPedidos : new ArrayList<>();
    }

    // --- Getters & Setters Auxiliares ---
    public void setNome(String nome) { this.nome = nome; }
    
    public TipoCozinha getTipo() { return tipo; }
    public void setTipo(TipoCozinha tipo) { this.tipo = tipo; }
    
    public Boolean getAtiva() { return ativa; }
    public void setAtiva(Boolean ativa) { this.ativa = ativa; }
    
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    
    public String getImpressoraId() { return impressoraId; }
    public void setImpressoraId(String impressoraId) { this.impressoraId = impressoraId; }
    
    public void setUnidadesAtendimento(List<UnidadeAtendimento> unidadesAtendimento) { this.unidadesAtendimento = unidadesAtendimento; }
    
    public List<SubPedido> getSubPedidos() { return subPedidos; }
    public void setSubPedidos(List<SubPedido> subPedidos) { this.subPedidos = subPedidos; }

    // --- Equals & HashCode ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Cozinha cozinha = (Cozinha) o;
        return java.util.Objects.equals(nome, cozinha.nome) &&
               tipo == cozinha.tipo &&
               java.util.Objects.equals(ativa, cozinha.ativa) &&
               java.util.Objects.equals(descricao, cozinha.descricao) &&
               java.util.Objects.equals(impressoraId, cozinha.impressoraId) &&
               java.util.Objects.equals(unidadesAtendimento, cozinha.unidadesAtendimento) &&
               java.util.Objects.equals(subPedidos, cozinha.subPedidos);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), nome, tipo, ativa, descricao, impressoraId, unidadesAtendimento, subPedidos);
    }

    // --- Builder ---
    public static CozinhaBuilder builder() {
        return new CozinhaBuilder();
    }

    public static class CozinhaBuilder {
        private String nome;
        private TipoCozinha tipo;
        private Boolean ativa;
        private String descricao;
        private String impressoraId;
        private List<UnidadeAtendimento> unidadesAtendimento;
        private List<SubPedido> subPedidos;

        CozinhaBuilder() {}

        public CozinhaBuilder nome(String nome) {
            this.nome = nome;
            return this;
        }

        public CozinhaBuilder tipo(TipoCozinha tipo) {
            this.tipo = tipo;
            return this;
        }

        public CozinhaBuilder ativa(Boolean ativa) {
            this.ativa = ativa;
            return this;
        }

        public CozinhaBuilder descricao(String descricao) {
            this.descricao = descricao;
            return this;
        }

        public CozinhaBuilder impressoraId(String impressoraId) {
            this.impressoraId = impressoraId;
            return this;
        }

        public CozinhaBuilder unidadesAtendimento(List<UnidadeAtendimento> unidadesAtendimento) {
            this.unidadesAtendimento = unidadesAtendimento;
            return this;
        }

        public CozinhaBuilder subPedidos(List<SubPedido> subPedidos) {
            this.subPedidos = subPedidos;
            return this;
        }

        public Cozinha build() {
            return new Cozinha(nome, tipo, ativa, descricao, impressoraId, unidadesAtendimento, subPedidos);
        }
    }
}
