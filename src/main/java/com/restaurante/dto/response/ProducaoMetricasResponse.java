package com.restaurante.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

public record ProducaoMetricasResponse(
        LocalDateTime de,
        LocalDateTime ate,
        long totalSubPedidos,
        Map<String, Long> porStatus,
        Long tempoMedioAteIniciarSegundos,
        Long tempoMedioAteProntoSegundos,
        Long tempoMedioTotalPreparacaoSegundos,
        long subPedidosAtrasados,
        java.util.List<UnidadeMetricas> porUnidade
) {
    public record UnidadeMetricas(
            Long unidadeProducaoId,
            String unidadeProducaoNome,
            long totalSubPedidos,
            Map<String, Long> porStatus,
            Long tempoMedioAteIniciarSegundos,
            Long tempoMedioAteProntoSegundos,
            Long tempoMedioTotalPreparacaoSegundos,
            long subPedidosAtrasados
    ) {}
}

