package com.restaurante.model.entity;

import com.restaurante.model.enums.TipoSessao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenant_sessao_consumo_configs", indexes = {
        @Index(name = "uq_tenant_sessao_consumo_config_tenant", columnList = "tenant_id", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantSessaoConsumoConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "permitir_pre_pago", nullable = false)
    private boolean permitirPrePago = true;

    @Column(name = "permitir_pos_pago", nullable = false)
    private boolean permitirPosPago = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_sessao_padrao", nullable = false, length = 20)
    private TipoSessao tipoSessaoPadrao = TipoSessao.POS_PAGO;

    @Column(name = "exigir_saldo_para_pedido", nullable = false)
    private boolean exigirSaldoParaPedido = false;

    @Column(name = "permitir_modo_anonimo", nullable = false)
    private boolean permitirModoAnonimo = true;

    @Column(name = "permitir_sessao_sem_mesa", nullable = false)
    private boolean permitirSessaoSemMesa = true;

    @Column(name = "permitir_sessao_com_mesa", nullable = false)
    private boolean permitirSessaoComMesa = true;

    @Column(name = "expiracao_horas", nullable = false)
    private Integer expiracaoHoras = 12;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;
}
