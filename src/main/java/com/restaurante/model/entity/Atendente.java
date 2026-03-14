package com.restaurante.model.entity;

import com.restaurante.model.enums.TipoUsuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Objects;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidade Atendente
 * Representa um funcionário que opera o painel administrativo
 * Pode criar mesas manualmente e gerenciar pedidos
 */
@Entity
@Table(name = "atendentes", indexes = {
    @Index(name = "idx_atendente_email", columnList = "email", unique = true),
    @Index(name = "idx_atendente_telefone", columnList = "telefone")
})
public class Atendente extends BaseEntity {

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
    @Column(nullable = false, length = 100)
    private String nome;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Size(max = 20)
    @Column(length = 20)
    private String telefone;

    @NotBlank(message = "Senha é obrigatória")
    @Column(nullable = false)
    private String senha; // Será hasheada na camada de serviço

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false)
    private TipoUsuario tipoUsuario = TipoUsuario.ATENDENTE;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    // Sessões de consumo abertas por este atendente
    @OneToMany(mappedBy = "aberturaPor", fetch = FetchType.LAZY)
    private List<SessaoConsumo> sessoesAbertas = new ArrayList<>();

    public Atendente() {
    }

    public Atendente(String nome, String email, String telefone, String senha, TipoUsuario tipoUsuario, Boolean ativo, List<SessaoConsumo> sessoesAbertas) {
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.senha = senha;
        if (tipoUsuario != null) this.tipoUsuario = tipoUsuario;
        if (ativo != null) this.ativo = ativo;
        if (sessoesAbertas != null) this.sessoesAbertas = sessoesAbertas;
    }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
    public TipoUsuario getTipoUsuario() { return tipoUsuario; }
    public void setTipoUsuario(TipoUsuario tipoUsuario) { this.tipoUsuario = tipoUsuario; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public List<SessaoConsumo> getSessoesAbertas() { return sessoesAbertas; }
    public void setSessoesAbertas(List<SessaoConsumo> sessoesAbertas) { this.sessoesAbertas = sessoesAbertas; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Atendente)) return false;
        if (!super.equals(o)) return false;
        Atendente atendente = (Atendente) o;
        return Objects.equals(nome, atendente.nome) && Objects.equals(email, atendente.email) && Objects.equals(telefone, atendente.telefone) && Objects.equals(senha, atendente.senha) && tipoUsuario == atendente.tipoUsuario && Objects.equals(ativo, atendente.ativo) && Objects.equals(sessoesAbertas, atendente.sessoesAbertas);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), nome, email, telefone, senha, tipoUsuario, ativo, sessoesAbertas);
    }

    public static AtendenteBuilder builder() {
        return new AtendenteBuilder();
    }

    public static class AtendenteBuilder {
        private String nome;
        private String email;
        private String telefone;
        private String senha;
        private TipoUsuario tipoUsuario = TipoUsuario.ATENDENTE;
        private Boolean ativo = true;
        private List<SessaoConsumo> sessoesAbertas = new ArrayList<>();

        public AtendenteBuilder nome(String nome) { this.nome = nome; return this; }
        public AtendenteBuilder email(String email) { this.email = email; return this; }
        public AtendenteBuilder telefone(String telefone) { this.telefone = telefone; return this; }
        public AtendenteBuilder senha(String senha) { this.senha = senha; return this; }
        public AtendenteBuilder tipoUsuario(TipoUsuario tipoUsuario) { this.tipoUsuario = tipoUsuario; return this; }
        public AtendenteBuilder ativo(Boolean ativo) { this.ativo = ativo; return this; }
        public AtendenteBuilder sessoesAbertas(List<SessaoConsumo> sessoesAbertas) { this.sessoesAbertas = sessoesAbertas; return this; }

        public Atendente build() {
            return new Atendente(nome, email, telefone, senha, tipoUsuario, ativo, sessoesAbertas);
        }
    }
}
