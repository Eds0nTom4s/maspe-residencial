package com.restaurante.model.entity;

import com.restaurante.model.enums.TipoUnidadeAtendimento;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity UnidadeDeAtendimento
 * 
 * Representa o ponto de entrada do pedido no sistema
 * Exemplos: Restaurante, Bar, Cafeteria, Room Service, Evento
 * 
 * Responsabilidades:
 * - Receber pedidos
 * - Coordenar atendimento
 * - Manter visão operacional
 * - Vincular cozinhas responsáveis
 */
@Entity
@Table(name = "unidades_atendimento", indexes = {
    @Index(name = "idx_unidade_tipo", columnList = "tipo"),
    @Index(name = "idx_unidade_ativa", columnList = "ativa")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnidadeAtendimento extends BaseEntity {

    @NotBlank(message = "Nome da unidade é obrigatório")
    @Column(nullable = false, length = 100)
    private String nome;

    @NotNull(message = "Tipo da unidade é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoUnidadeAtendimento tipo;

    @Column(name = "ativa", nullable = false)
    @Builder.Default
    private Boolean ativa = true;

    @Column(length = 500)
    private String descricao;

    /**
     * Relacionamento com Cozinhas responsáveis
     * Uma unidade pode ter múltiplas cozinhas
     * Uma cozinha pode atender múltiplas unidades
     */
    @ManyToMany
    @JoinTable(
        name = "unidade_cozinha",
        joinColumns = @JoinColumn(name = "unidade_id"),
        inverseJoinColumns = @JoinColumn(name = "cozinha_id")
    )
    @Builder.Default
    private List<Cozinha> cozinhas = new ArrayList<>();

    /**
     * Relacionamento com unidades de consumo desta unidade
     */
    @OneToMany(mappedBy = "unidadeAtendimento", fetch = FetchType.LAZY)
    @Builder.Default
    private List<UnidadeDeConsumo> unidadesConsumo = new ArrayList<>();

    /**
     * Verifica se a unidade está operacional
     */
    public boolean isOperacional() {
        return ativa && !cozinhas.isEmpty();
    }

    /**
     * Adiciona cozinha responsável
     */
    public void adicionarCozinha(Cozinha cozinha) {
        if (!cozinhas.contains(cozinha)) {
            cozinhas.add(cozinha);
            cozinha.getUnidadesAtendimento().add(this);
        }
    }

    /**
     * Remove cozinha responsável
     */
    public void removerCozinha(Cozinha cozinha) {
        cozinhas.remove(cozinha);
        cozinha.getUnidadesAtendimento().remove(this);
    }
}
