package com.restaurante.model.entity;

import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "turnos_operacionais", indexes = {
        @Index(name = "idx_turno_tenant", columnList = "tenant_id"),
        @Index(name = "idx_turno_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_turno_tenant_inst_ua", columnList = "tenant_id, instituicao_id, unidade_atendimento_id"),
        @Index(name = "idx_turno_tenant_aberto_em", columnList = "tenant_id, aberto_em")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TurnoOperacional extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instituicao_id", nullable = false)
    private Instituicao instituicao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_atendimento_id", nullable = false)
    private UnidadeAtendimento unidadeAtendimento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aberto_por_user_id", nullable = false)
    private User abertoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fechado_por_user_id")
    private User fechadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispositivo_abertura_id")
    private DispositivoOperacional dispositivoAbertura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispositivo_fecho_id")
    private DispositivoOperacional dispositivoFecho;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TurnoOperacionalStatus status = TurnoOperacionalStatus.ABERTO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TurnoOperacionalTipo tipo = TurnoOperacionalTipo.OUTRO;

    @NotBlank
    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @NotNull
    @Column(name = "aberto_em", nullable = false)
    private LocalDateTime abertoEm;

    @Column(name = "fechado_em")
    private LocalDateTime fechadoEm;

    @Column(name = "observacao_abertura", length = 500)
    private String observacaoAbertura;

    @Column(name = "observacao_fecho", length = 500)
    private String observacaoFecho;

    @Column(name = "resumo_json", columnDefinition = "text")
    private String resumoJson;
}

