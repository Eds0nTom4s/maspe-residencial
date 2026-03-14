package com.restaurante.model.entity;

import com.restaurante.model.enums.TipoUsuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entidade Cliente
 * Representa um cliente que acessa o sistema via QR Code
 * Cliente é identificado pelo número de telefone e autenticado via OTP
 */
@Entity
@Table(name = "clientes", indexes = {
    @Index(name = "idx_cliente_telefone", columnList = "telefone", unique = true)
})
public class Cliente extends BaseEntity {

    @NotBlank(message = "Telefone é obrigatório")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Formato de telefone inválido")
    @Column(nullable = false, unique = true, length = 20)
    private String telefone;

    @Size(max = 100)
    @Column(length = 100)
    private String nome;

    @Column(name = "telefone_verificado", nullable = false)
    private Boolean telefoneVerificado = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false)
    private TipoUsuario tipoUsuario = TipoUsuario.CLIENTE;

    @Column(name = "otp_code", length = 10)
    private String otpCode;

    @Column(name = "otp_expiration")
    private LocalDateTime otpExpiration;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UnidadeDeConsumo> unidadesConsumo = new ArrayList<>();

    public Cliente() {}

    public Cliente(String telefone, String nome, Boolean telefoneVerificado, TipoUsuario tipoUsuario,
                   String otpCode, LocalDateTime otpExpiration, Boolean ativo, List<UnidadeDeConsumo> unidadesConsumo) {
        this.telefone = telefone;
        this.nome = nome;
        if (telefoneVerificado != null) this.telefoneVerificado = telefoneVerificado;
        if (tipoUsuario != null) this.tipoUsuario = tipoUsuario;
        this.otpCode = otpCode;
        this.otpExpiration = otpExpiration;
        if (ativo != null) this.ativo = ativo;
        if (unidadesConsumo != null) this.unidadesConsumo = unidadesConsumo;
    }

    /** Verifica se o OTP ainda é válido */
    public boolean isOtpValido(String otp) {
        return this.otpCode != null
            && this.otpCode.equals(otp)
            && this.otpExpiration != null
            && this.otpExpiration.isAfter(LocalDateTime.now());
    }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Boolean getTelefoneVerificado() { return telefoneVerificado; }
    public void setTelefoneVerificado(boolean telefoneVerificado) { this.telefoneVerificado = telefoneVerificado; }
    public TipoUsuario getTipoUsuario() { return tipoUsuario; }
    public void setTipoUsuario(TipoUsuario tipoUsuario) { this.tipoUsuario = tipoUsuario; }
    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public LocalDateTime getOtpExpiration() { return otpExpiration; }
    public void setOtpExpiration(LocalDateTime otpExpiration) { this.otpExpiration = otpExpiration; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public List<UnidadeDeConsumo> getUnidadesConsumo() { return unidadesConsumo; }
    public void setUnidadesConsumo(List<UnidadeDeConsumo> unidadesConsumo) { this.unidadesConsumo = unidadesConsumo; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cliente)) return false;
        if (!super.equals(o)) return false;
        Cliente cliente = (Cliente) o;
        return Objects.equals(telefone, cliente.telefone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), telefone);
    }

    public static ClienteBuilder builder() { return new ClienteBuilder(); }

    public static class ClienteBuilder {
        private String telefone;
        private String nome;
        private Boolean telefoneVerificado = false;
        private TipoUsuario tipoUsuario = TipoUsuario.CLIENTE;
        private String otpCode;
        private LocalDateTime otpExpiration;
        private Boolean ativo = true;
        private List<UnidadeDeConsumo> unidadesConsumo = new ArrayList<>();

        public ClienteBuilder telefone(String telefone) { this.telefone = telefone; return this; }
        public ClienteBuilder nome(String nome) { this.nome = nome; return this; }
        public ClienteBuilder telefoneVerificado(Boolean v) { this.telefoneVerificado = v; return this; }
        public ClienteBuilder tipoUsuario(TipoUsuario t) { this.tipoUsuario = t; return this; }
        public ClienteBuilder otpCode(String otpCode) { this.otpCode = otpCode; return this; }
        public ClienteBuilder otpExpiration(LocalDateTime otpExpiration) { this.otpExpiration = otpExpiration; return this; }
        public ClienteBuilder ativo(Boolean ativo) { this.ativo = ativo; return this; }
        public ClienteBuilder unidadesConsumo(List<UnidadeDeConsumo> list) { this.unidadesConsumo = list; return this; }

        public Cliente build() {
            return new Cliente(telefone, nome, telefoneVerificado, tipoUsuario, otpCode, otpExpiration, ativo, unidadesConsumo);
        }
    }
}
