package com.restaurante.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;

/**
 * Request para abertura de uma nova SessaoConsumo.
 *
 * <p>A sessão pode ser aberta com ou sem mesa física associada:
 * <ul>
 *   <li><b>Com mesa</b>: informe {@code mesaId}. A unidade de atendimento é derivada da mesa.</li>
 *   <li><b>Sem mesa</b>: omita {@code mesaId} e informe {@code unidadeAtendimentoId} diretamente.</li>
 * </ul>
 *
 * <p>Fluxos de portador:
 * <ul>
 *   <li><b>Identificado</b>: {@code modoAnonimo = false} — exige {@code telefoneCliente}.</li>
 *   <li><b>Anônimo</b>: {@code modoAnonimo = true} — nenhum cliente necessário;</li>
 * </ul>
 */
public class AbrirSessaoRequest {

    /** ID da mesa (opcional). Quando presente, a unidade de atendimento é derivada da mesa. */
    private Long mesaId;

    /** ID da unidade de atendimento (obrigatório quando mesaId é nulo). */
    private Long unidadeAtendimentoId;

    /**
     * Telefone do cliente para o fluxo identificado.
     * Nulo/vazio quando {@code modoAnonimo = true}.
     */
    private String telefoneCliente;

    /**
     * Tipo de sessão (Pré-pago / Pós-pago).
     * Caso não seja informado, assume PRE_PAGO por default.
     */
    private com.restaurante.model.enums.TipoSessao tipoSessao;

    /**
     * Ativa o modo de consumo anônimo (sem identidade do cliente).
     * Quando {@code true}: pós-pago bloqueado; QR Code é o único identificador.
     */
    private boolean modoAnonimo = false;

    /**
     * ID do atendente que abriu a sessão (opcional — auditoria).
     */
    private Long atendenteId;

    public AbrirSessaoRequest() {}

    public AbrirSessaoRequest(Long mesaId, Long unidadeAtendimentoId, String telefoneCliente, com.restaurante.model.enums.TipoSessao tipoSessao, boolean modoAnonimo, Long atendenteId) {
        this.mesaId = mesaId;
        this.unidadeAtendimentoId = unidadeAtendimentoId;
        this.telefoneCliente = telefoneCliente;
        this.tipoSessao = tipoSessao;
        this.modoAnonimo = modoAnonimo;
        this.atendenteId = atendenteId;
    }

    public Long getMesaId() { return mesaId; }
    public void setMesaId(Long mesaId) { this.mesaId = mesaId; }

    public Long getUnidadeAtendimentoId() { return unidadeAtendimentoId; }
    public void setUnidadeAtendimentoId(Long unidadeAtendimentoId) { this.unidadeAtendimentoId = unidadeAtendimentoId; }

    public String getTelefoneCliente() { return telefoneCliente; }
    public void setTelefoneCliente(String telefoneCliente) { this.telefoneCliente = telefoneCliente; }

    public com.restaurante.model.enums.TipoSessao getTipoSessao() { return tipoSessao; }
    public void setTipoSessao(com.restaurante.model.enums.TipoSessao tipoSessao) { this.tipoSessao = tipoSessao; }

    public boolean isModoAnonimo() { return modoAnonimo; }
    public boolean getModoAnonimo() { return modoAnonimo; }
    public void setModoAnonimo(boolean modoAnonimo) { this.modoAnonimo = modoAnonimo; }

    public Long getAtendenteId() { return atendenteId; }
    public void setAtendenteId(Long atendenteId) { this.atendenteId = atendenteId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbrirSessaoRequest that = (AbrirSessaoRequest) o;
        return modoAnonimo == that.modoAnonimo &&
               Objects.equals(mesaId, that.mesaId) &&
               Objects.equals(unidadeAtendimentoId, that.unidadeAtendimentoId) &&
               Objects.equals(telefoneCliente, that.telefoneCliente) &&
               tipoSessao == that.tipoSessao &&
               Objects.equals(atendenteId, that.atendenteId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mesaId, unidadeAtendimentoId, telefoneCliente, tipoSessao, modoAnonimo, atendenteId);
    }

    public static AbrirSessaoRequestBuilder builder() {
        return new AbrirSessaoRequestBuilder();
    }

    public static class AbrirSessaoRequestBuilder {
        private Long mesaId;
        private Long unidadeAtendimentoId;
        private String telefoneCliente;
        private com.restaurante.model.enums.TipoSessao tipoSessao;
        private boolean modoAnonimo;
        private Long atendenteId;

        AbrirSessaoRequestBuilder() {}

        public AbrirSessaoRequestBuilder mesaId(Long mesaId) {
            this.mesaId = mesaId;
            return this;
        }

        public AbrirSessaoRequestBuilder unidadeAtendimentoId(Long unidadeAtendimentoId) {
            this.unidadeAtendimentoId = unidadeAtendimentoId;
            return this;
        }

        public AbrirSessaoRequestBuilder telefoneCliente(String telefoneCliente) {
            this.telefoneCliente = telefoneCliente;
            return this;
        }

        public AbrirSessaoRequestBuilder tipoSessao(com.restaurante.model.enums.TipoSessao tipoSessao) {
            this.tipoSessao = tipoSessao;
            return this;
        }

        public AbrirSessaoRequestBuilder modoAnonimo(boolean modoAnonimo) {
            this.modoAnonimo = modoAnonimo;
            return this;
        }

        public AbrirSessaoRequestBuilder atendenteId(Long atendenteId) {
            this.atendenteId = atendenteId;
            return this;
        }

        public AbrirSessaoRequest build() {
            return new AbrirSessaoRequest(this.mesaId, this.unidadeAtendimentoId, this.telefoneCliente, this.tipoSessao, this.modoAnonimo, this.atendenteId);
        }
    }
}
