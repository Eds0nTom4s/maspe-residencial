package com.restaurante.dto.request;

import com.restaurante.model.enums.TipoUnidadeConsumo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para criação de uma nova Mesa física.
 * Operação exclusiva do ADMIN.
 * A mesa representa um recurso permanente — criada uma única vez.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriarMesaRequest {

    @NotBlank(message = "Referência é obrigatória")
    private String referencia; // Ex: "Mesa 15", "Área VIP"

    private TipoUnidadeConsumo tipo; // Default: MESA_FISICA

    private Integer numero; // Número da mesa (quando aplicável)

    /**
     * QR Code fixo da mesa.
     * Se não informado, pode ser gerado posteriormente pelo módulo de QR Codes.
     */
    private String qrCode;

    private Integer capacidade;

    @NotNull(message = "Unidade de atendimento é obrigatória")
    private Long unidadeAtendimentoId;
}
