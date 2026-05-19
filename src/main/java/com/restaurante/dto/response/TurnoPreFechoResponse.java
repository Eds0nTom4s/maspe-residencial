package com.restaurante.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class TurnoPreFechoResponse {
    private boolean podeFechar;
    private List<String> bloqueios = new ArrayList<>();
    private List<String> avisos = new ArrayList<>();

    private Map<String, Long> pedidosPorStatus;
    private Map<String, Long> subPedidosPorStatus;
    private Map<String, Long> pagamentosPorStatus;

    private long sessoesAbertas;
    private long dispositivosOffline;
}

