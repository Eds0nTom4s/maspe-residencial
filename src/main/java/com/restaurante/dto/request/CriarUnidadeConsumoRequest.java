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

    /**
     * Telefone do cliente para o fluxo identificado (OTP).
     * Nulo quando {@code modoAnonimo = true}.
     */
    private String telefoneCliente;

    /**
     * Ativa o modo de consumo anónimo.
     *
     * Quando {@code true}:
     * - {@code telefoneCliente} é ignorado
     * - O QR Code é o único identificador do portador
     * - Pós-pago bloqueado automaticamente
     * - Perda do QR = perda do saldo (sem recuperação)
     */
    @Builder.Default
    private boolean modoAnonimo = false;

    @NotNull(message = "Unidade de atendimento é obrigatória")
    private Long unidadeAtendimentoId;

    private String qrCode;

    private Integer capacidade;

    private Long atendenteId; // Opcional - se foi criada manualmente
}
