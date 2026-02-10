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
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cozinha extends BaseEntity {

    @NotBlank(message = "Nome da cozinha é obrigatório")
    @Column(nullable = false, length = 100)
    private String nome;

    @NotNull(message = "Tipo da cozinha é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoCozinha tipo;

    @Column(name = "ativa", nullable = false)
    @Builder.Default
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
    @Builder.Default
    private List<UnidadeAtendimento> unidadesAtendimento = new ArrayList<>();

    /**
     * Relacionamento com SubPedidos desta cozinha
     */
    @OneToMany(mappedBy = "cozinha", fetch = FetchType.LAZY)
    @Builder.Default
    private List<SubPedido> subPedidos = new ArrayList<>();

    /**
     * Verifica se a cozinha está operacional
     */
    public boolean isOperacional() {
        return ativa;
    }
}
