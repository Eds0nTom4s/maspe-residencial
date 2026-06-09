package com.restaurante.dto.response;

import com.restaurante.model.enums.TipoSessao;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TenantSessaoConsumoConfigResponse {

    private Long tenantId;
    private boolean enabled;
    private boolean permitirPrePago;
    private boolean permitirPosPago;
    private TipoSessao tipoSessaoPadrao;
    private boolean exigirSaldoParaPedido;
    private boolean permitirModoAnonimo;
    private boolean permitirSessaoSemMesa;
    private boolean permitirSessaoComMesa;
    private Integer expiracaoHoras;
    private Long updatedByUserId;
    private LocalDateTime atualizadoEm;
}
