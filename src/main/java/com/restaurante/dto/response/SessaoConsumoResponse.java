package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusSessaoConsumo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de resposta para SessaoConsumo.
 *
 * <p>Representa o evento temporal de ocupação de uma mesa.
 * Auditável: preserva toda a trilha da sessão mesmo após encerramento.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessaoConsumoResponse {

    private Long id;

    // Mesa associada
    private Long mesaId;
    private String referenciaMesa;

    // Cliente (null no fluxo anônimo)
    private Long clienteId;
    private String nomeCliente;
    private String telefoneCliente;

    // Atendente que abriu (null se auto-atendimento)
    private Long aberturaPorId;
    private String nomeAtendente;

    // Ciclo de vida
    private LocalDateTime abertaEm;
    private LocalDateTime fechadaEm;
    private StatusSessaoConsumo status;

    // Modo anônimo
    private Boolean modoAnonimo;
    private String qrCodePortador;

    // Totalizador do consumo (calculado a partir dos pedidos)
    private BigDecimal totalConsumo;
}
