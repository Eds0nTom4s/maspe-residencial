package com.restaurante.model.entity;

import com.restaurante.model.enums.TipoUsuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidade Atendente
 * Representa um funcionário que opera o painel administrativo
 * Pode criar mesas manualmente e gerenciar pedidos
 */
@Entity
@Table(name = "atendentes", indexes = {
    @Index(name = "idx_atendente_email", columnList = "email", unique = true),
    @Index(name = "idx_atendente_telefone", columnList = "telefone")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Atendente extends BaseEntity {

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
    @Column(nullable = false, length = 100)
    private String nome;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Size(max = 20)
    @Column(length = 20)
    private String telefone;

    @NotBlank(message = "Senha é obrigatória")
    @Column(nullable = false)
    private String senha; // Será hasheada na camada de serviço

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false)
    @Builder.Default
    private TipoUsuario tipoUsuario = TipoUsuario.ATENDENTE;

    @Column(name = "ativo", nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    // Relacionamento com unidades de consumo criadas manualmente
    @OneToMany(mappedBy = "atendente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<UnidadeDeConsumo> unidadesConsumo = new ArrayList<>();
}
