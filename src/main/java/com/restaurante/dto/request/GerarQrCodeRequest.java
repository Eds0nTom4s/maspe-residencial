package com.restaurante.dto.request;

import com.restaurante.model.enums.TipoQrCode;
import jakarta.validation.constraints.NotNull;
public class GerarQrCodeRequest {

    public GerarQrCodeRequest() {
    }

    public GerarQrCodeRequest(TipoQrCode tipo, Long mesaId, Long pedidoId, Long validadeMinutos, String metadados) {
        this.tipo = tipo;
        this.mesaId = mesaId;
        this.pedidoId = pedidoId;
        this.validadeMinutos = validadeMinutos;
        this.metadados = metadados;
    }

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

    public TipoQrCode getTipo() {
        return tipo;
    }

    public void setTipo(TipoQrCode tipo) {
        this.tipo = tipo;
    }

    public Long getMesaId() {
        return mesaId;
    }

    public void setMesaId(Long mesaId) {
        this.mesaId = mesaId;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public void setPedidoId(Long pedidoId) {
        this.pedidoId = pedidoId;
    }

    public Long getValidadeMinutos() {
        return validadeMinutos;
    }

    public void setValidadeMinutos(Long validadeMinutos) {
        this.validadeMinutos = validadeMinutos;
    }

    public String getMetadados() {
        return metadados;
    }

    public void setMetadados(String metadados) {
        this.metadados = metadados;
    }
}
