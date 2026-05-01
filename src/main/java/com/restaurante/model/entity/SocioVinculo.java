package com.restaurante.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * SocioVinculo — Mapeamento de identidade entre o App de Sócios (Associagest) e o motor.
 *
 * <p>Esta entidade serve como tabela de vínculo leve. Quando um sócio acede à Loja
 * pela primeira vez (token JWT do Associagest), o {@code SocioAuthFilter} regista aqui
 * a sua identidade mínima e mapeia para um {@link Cliente} existente (pelo telefone) ou
 * cria um novo.
 *
 * <p><b>Invariantes:</b>
 * <ul>
 *   <li>{@code socioId} é único — ID externo vindo do Associagest</li>
 *   <li>{@code telefone} é único — chave de mapeamento para {@link Cliente}</li>
 *   <li>{@code primeiroAcessoEm} é imutável após criação</li>
 *   <li>Não há dados sensíveis — este registo não substitui o Cliente</li>
 * </ul>
 */
@Entity
@Table(name = "socios_vinculo", indexes = {
    @Index(name = "idx_socio_vinculo_socio_id", columnList = "socio_id", unique = true),
    @Index(name = "idx_socio_vinculo_telefone", columnList = "telefone", unique = true)
})
public class SocioVinculo extends BaseEntity {

    /**
     * ID externo do sócio no sistema Associagest.
     * Extraído do claim {@code sub} do JWT emitido pelo Associagest.
     */
    @NotBlank
    @Column(name = "socio_id", nullable = false, unique = true, length = 100)
    private String socioId;

    /**
     * Nome completo do sócio (sincronizado do JWT na primeira ligação).
     */
    @Column(name = "nome", length = 150)
    private String nome;

    /**
     * Telefone do sócio — chave primária de mapeamento para a entidade {@link Cliente}.
     */
    @NotBlank
    @Column(name = "telefone", nullable = false, unique = true, length = 20)
    private String telefone;

    /**
     * Email do sócio (opcional — pode não existir em todos os perfis).
     */
    @Column(name = "email", length = 200)
    private String email;

    /**
     * Timestamp do primeiro acesso à loja via token Associagest.
     * Imutável após criação.
     */
    @Column(name = "primeiro_acesso_em", nullable = false, updatable = false)
    private LocalDateTime primeiroAcessoEm = LocalDateTime.now();

    /**
     * Timestamp do último acesso (actualizado a cada request autenticado).
     */
    @Column(name = "ultimo_acesso_em")
    private LocalDateTime ultimoAcessoEm;

    // ── Construtores ─────────────────────────────────────────────────────────

    public SocioVinculo() {}

    public SocioVinculo(String socioId, String nome, String telefone, String email) {
        this.socioId = socioId;
        this.nome = nome;
        this.telefone = telefone;
        this.email = email;
        this.primeiroAcessoEm = LocalDateTime.now();
        this.ultimoAcessoEm = LocalDateTime.now();
    }

    // ── Métodos de negócio ────────────────────────────────────────────────────

    /**
     * Actualiza os dados do sócio com base no JWT mais recente.
     * Chamado a cada acesso para manter nome e email sincronizados.
     */
    public void actualizarDados(String nome, String email) {
        if (nome != null && !nome.isBlank()) this.nome = nome;
        if (email != null && !email.isBlank()) this.email = email;
        this.ultimoAcessoEm = LocalDateTime.now();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getSocioId() { return socioId; }
    public void setSocioId(String socioId) { this.socioId = socioId; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public LocalDateTime getPrimeiroAcessoEm() { return primeiroAcessoEm; }

    public LocalDateTime getUltimoAcessoEm() { return ultimoAcessoEm; }
    public void setUltimoAcessoEm(LocalDateTime ultimoAcessoEm) { this.ultimoAcessoEm = ultimoAcessoEm; }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SocioVinculo)) return false;
        if (!super.equals(o)) return false;
        SocioVinculo that = (SocioVinculo) o;
        return Objects.equals(socioId, that.socioId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), socioId);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String socioId;
        private String nome;
        private String telefone;
        private String email;

        public Builder socioId(String socioId) { this.socioId = socioId; return this; }
        public Builder nome(String nome) { this.nome = nome; return this; }
        public Builder telefone(String telefone) { this.telefone = telefone; return this; }
        public Builder email(String email) { this.email = email; return this; }

        public SocioVinculo build() {
            return new SocioVinculo(socioId, nome, telefone, email);
        }
    }
}
