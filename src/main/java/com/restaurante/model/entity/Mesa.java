package com.restaurante.model.entity;

import com.restaurante.model.enums.TipoUnidadeConsumo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidade Mesa — recurso físico PERMANENTE.
 *
 * <p>Representa uma mesa (ou ponto de consumo) do mundo real.
 * É criada uma única vez pelo ADMIN e NUNCA é "finalizada" ou "fechada".
 *
 * <p>Invariantes:
 * <ul>
 *   <li>Nunca possui status de ocupação persistido.</li>
 *   <li>O QR Code é fixo e vinculado à mesa, não à sessão.</li>
 *   <li>Status de ocupação é DERIVADO via SessaoConsumo:
 *       <pre>OCUPADA ≡ EXISTS SessaoConsumo WHERE mesa = this AND status = ABERTA</pre>
 *   </li>
 *   <li>Desativar ({@code ativa = false}) é a única operação administrativa permitida.</li>
 * </ul>
 */
@Entity
@Table(name = "mesas", indexes = {
    @Index(name = "idx_mesa_referencia", columnList = "referencia"),
    @Index(name = "idx_mesa_qr_code", columnList = "qr_code", unique = true),
    @Index(name = "idx_mesa_ativa", columnList = "ativa"),
    @Index(name = "idx_mesa_unidade_atendimento", columnList = "unidade_atendimento_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mesa extends BaseEntity {

    /**
     * Referência humana da mesa.
     * Exemplos: "Mesa 10", "Quarto 205", "Área VIP"
     */
    @NotBlank(message = "Referência da mesa é obrigatória")
    @Column(nullable = false, length = 100)
    private String referencia;

    /**
     * Número da mesa (quando aplicável).
     */
    @Column(name = "numero")
    private Integer numero;

    /**
     * QR Code fixo da mesa — nunca muda entre sessões.
     * Escanear este código inicia uma nova SessaoConsumo.
     */
    @Column(name = "qr_code", unique = true, length = 100)
    private String qrCode;

    /**
     * Capacidade máxima de pessoas.
     */
    @Column(name = "capacidade")
    private Integer capacidade;

    /**
     * Flag de ativação administrativa.
     * Mesas inativas não podem receber novas sessões.
     */
    @Column(name = "ativa", nullable = false)
    @Builder.Default
    private Boolean ativa = true;

    /**
     * Tipo de ponto de consumo.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TipoUnidadeConsumo tipo = TipoUnidadeConsumo.MESA_FISICA;

    /**
     * Unidade de atendimento (restaurante / bar / evento) à qual a mesa pertence.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id", nullable = false)
    private UnidadeAtendimento unidadeAtendimento;

    /**
     * Histórico de sessões de consumo desta mesa.
     * Apenas leitura — gerenciado pelo lado proprietário (SessaoConsumo).
     */
    @OneToMany(mappedBy = "mesa", fetch = FetchType.LAZY)
    @Builder.Default
    private List<SessaoConsumo> sessoes = new ArrayList<>();

    /**
     * Retorna identificador legível: número ou referência.
     */
    public String getIdentificador() {
        return numero != null ? numero.toString() : referencia;
    }
}
