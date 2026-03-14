package com.restaurante.model.entity;

import com.restaurante.model.enums.TipoEventoSessao;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

/**
 * Entidade de auditoria para registar o histórico de eventos de uma Sessão de Consumo.
 *
 * <p>Esta entidade é imutável do ponto de vista do domínio (append-only)
 * e garante o tracking de todas as ações importantes do ciclo de vida da sessão.
 */
@Entity
@Table(name = "eventos_sessao", indexes = {
    @Index(name = "idx_evento_sessao", columnList = "sessao_consumo_id"),
    @Index(name = "idx_evento_tipo", columnList = "tipo_evento"),
    @Index(name = "idx_evento_data", columnList = "created_at")
})
public class EventoSessao extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sessao_consumo_id", nullable = false)
    private SessaoConsumo sessaoConsumo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 50)
    @NotNull
    private TipoEventoSessao tipoEvento;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "usuario_responsavel")
    private String usuarioResponsavel;

    public EventoSessao() {}

    public EventoSessao(SessaoConsumo sessaoConsumo, TipoEventoSessao tipoEvento, String descricao, String usuarioResponsavel) {
        this.sessaoConsumo = sessaoConsumo;
        this.tipoEvento = tipoEvento;
        this.descricao = descricao;
        this.usuarioResponsavel = usuarioResponsavel;
    }

    public SessaoConsumo getSessaoConsumo() { return sessaoConsumo; }
    public void setSessaoConsumo(SessaoConsumo sessaoConsumo) { this.sessaoConsumo = sessaoConsumo; }

    public TipoEventoSessao getTipoEvento() { return tipoEvento; }
    public void setTipoEvento(TipoEventoSessao tipoEvento) { this.tipoEvento = tipoEvento; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getUsuarioResponsavel() { return usuarioResponsavel; }
    public void setUsuarioResponsavel(String usuarioResponsavel) { this.usuarioResponsavel = usuarioResponsavel; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        EventoSessao that = (EventoSessao) o;
        return Objects.equals(sessaoConsumo, that.sessaoConsumo) &&
               tipoEvento == that.tipoEvento &&
               Objects.equals(descricao, that.descricao);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sessaoConsumo, tipoEvento, descricao);
    }

    public static EventoSessaoBuilder builder() {
        return new EventoSessaoBuilder();
    }

    public static class EventoSessaoBuilder {
        private SessaoConsumo sessaoConsumo;
        private TipoEventoSessao tipoEvento;
        private String descricao;
        private String usuarioResponsavel;

        EventoSessaoBuilder() {}

        public EventoSessaoBuilder sessaoConsumo(SessaoConsumo sessaoConsumo) {
            this.sessaoConsumo = sessaoConsumo;
            return this;
        }

        public EventoSessaoBuilder tipoEvento(TipoEventoSessao tipoEvento) {
            this.tipoEvento = tipoEvento;
            return this;
        }

        public EventoSessaoBuilder descricao(String descricao) {
            this.descricao = descricao;
            return this;
        }

        public EventoSessaoBuilder usuarioResponsavel(String usuarioResponsavel) {
            this.usuarioResponsavel = usuarioResponsavel;
            return this;
        }

        public EventoSessao build() {
            return new EventoSessao(this.sessaoConsumo, this.tipoEvento, this.descricao, this.usuarioResponsavel);
        }
    }
}
