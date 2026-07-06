package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.PedidoAllowedAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.EnumMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantPedidoResumoResponse {
    private Long id;
    private String numero;
    private StatusPedido statusOperacional;
    private StatusFinanceiroPedido statusFinanceiro;
    private BigDecimal total;

    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private Long mesaId;
    private String mesaReferencia;

    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private LocalDateTime pagoEm;

    private int quantidadeItens;

    @Builder.Default
    private Set<PedidoAllowedAction> allowedActions = new LinkedHashSet<>();

    @Builder.Default
    private Map<PedidoAllowedAction, String> actionReasons = new EnumMap<>(PedidoAllowedAction.class);
}
