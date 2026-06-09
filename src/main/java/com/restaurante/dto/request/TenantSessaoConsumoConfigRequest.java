package com.restaurante.dto.request;

import com.restaurante.model.enums.TipoSessao;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TenantSessaoConsumoConfigRequest {

    @NotNull
    private Boolean permitirPrePago;

    @NotNull
    private Boolean permitirPosPago;

    @NotNull
    private TipoSessao tipoSessaoPadrao;

    @NotNull
    private Boolean exigirSaldoParaPedido;

    @NotNull
    private Boolean permitirModoAnonimo;

    @NotNull
    private Boolean permitirSessaoSemMesa;

    @NotNull
    private Boolean permitirSessaoComMesa;

    @NotNull
    @Min(1)
    @Max(168)
    private Integer expiracaoHoras;
}
