package com.restaurante.dto.response;

import com.restaurante.model.enums.TipoUnidadeConsumo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de resposta para Mesa.
 *
 * <p>O campo {@code status} é DERIVADO e calculado em tempo real:
 * <ul>
 *   <li>{@code "DISPONIVEL"} — nenhuma SessaoConsumo ABERTA para esta mesa.</li>
 *   <li>{@code "OCUPADA"}    — existe ao menos uma SessaoConsumo ABERTA.</li>
 * </ul>
 * Este valor nunca é persistido no banco de dados.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesaResponse {

    private Long id;
    private String referencia;
    private Integer numero;
    private String qrCode;
    private Integer capacidade;
    private Boolean ativa;
    private TipoUnidadeConsumo tipo;

    private Long unidadeAtendimentoId;
    private String unidadeAtendimentoNome;

    /**
     * Status derivado — calculado via SessaoConsumo, NUNCA persistido.
     * Valores possíveis: "DISPONIVEL" | "OCUPADA"
     */
    private String status;

    /**
     * ID da sessão de consumo atualmente aberta (null se DISPONIVEL).
     */
    private Long sessaoAtivaId;

    private LocalDateTime createdAt;
}
