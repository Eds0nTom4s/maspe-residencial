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

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public TipoUnidadeConsumo getTipo() { return tipo; }
    public void setTipo(TipoUnidadeConsumo tipo) { this.tipo = tipo; }

    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }

    public String getTelefoneCliente() { return telefoneCliente; }
    public void setTelefoneCliente(String telefoneCliente) { this.telefoneCliente = telefoneCliente; }

    public boolean isModoAnonimo() { return modoAnonimo; }
    public void setModoAnonimo(boolean modoAnonimo) { this.modoAnonimo = modoAnonimo; }

    public Long getUnidadeAtendimentoId() { return unidadeAtendimentoId; }
    public void setUnidadeAtendimentoId(Long unidadeAtendimentoId) { this.unidadeAtendimentoId = unidadeAtendimentoId; }

    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }

    public Integer getCapacidade() { return capacidade; }
    public void setCapacidade(Integer capacidade) { this.capacidade = capacidade; }

    public Long getAtendenteId() { return atendenteId; }
    public void setAtendenteId(Long atendenteId) { this.atendenteId = atendenteId; }
}
