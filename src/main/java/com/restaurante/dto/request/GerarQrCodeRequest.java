package com.restaurante.dto.request;

import com.restaurante.model.enums.TipoQrCode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GerarQrCodeRequest {

    @NotNull(message = "Tipo de QR Code é obrigatório")
    private TipoQrCode tipo;

    /**
     * ID da Mesa (obrigatório para tipo MESA)
     */
    private Long mesaId;

    /**
     * ID do Pedido (obrigatório para tipos ENTREGA e PAGAMENTO)
     */
    private Long pedidoId;

    /**
     * Validade customizada em minutos (opcional, usa padrão do tipo se null)
     */
    private Long validadeMinutos;

    /**
     * Metadados adicionais (opcional)
     */
    private String metadados;
}
