package com.restaurante.dto.request;

import com.restaurante.model.enums.TipoUnidadeConsumo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriarUnidadeConsumoRequest {

    @NotBlank(message = "Referência é obrigatória")
    private String referencia; // Ex: "Mesa 15", "Quarto 205", "Área VIP"

    private TipoUnidadeConsumo tipo; // Default: MESA_FISICA

    private Integer numero; // Número da unidade (quando aplicável)

    @NotBlank(message = "Telefone do cliente é obrigatório")
    private String telefoneCliente;

    @NotNull(message = "Unidade de atendimento é obrigatória")
    private Long unidadeAtendimentoId;

    private String qrCode;

    private Integer capacidade;

    private Long atendenteId; // Opcional - se foi criada manualmente
}
