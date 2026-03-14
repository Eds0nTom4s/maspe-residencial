package com.restaurante.dto.response;

import com.restaurante.model.enums.Role;

import java.time.LocalDateTime;
import java.util.Set;

public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String nomeCompleto;
    private String telefone;
    private Set<Role> roles;
    private Boolean ativo;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private LocalDateTime ultimoAcesso;

    public UserResponse() {}

    public UserResponse(Long id, String username, String email, String nomeCompleto, String telefone,
                        Set<Role> roles, Boolean ativo, LocalDateTime created_at, LocalDateTime updated_at,
                        LocalDateTime ultimoAcesso) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.nomeCompleto = nomeCompleto;
        this.telefone = telefone;
        this.roles = roles;
        this.ativo = ativo;
        this.created_at = created_at;
        this.updated_at = updated_at;
        this.ultimoAcesso = ultimoAcesso;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNomeCompleto() { return nomeCompleto; }
    public void setNomeCompleto(String nomeCompleto) { this.nomeCompleto = nomeCompleto; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public LocalDateTime getCreated_at() { return created_at; }
    public void setCreated_at(LocalDateTime created_at) { this.created_at = created_at; }
    public LocalDateTime getUpdated_at() { return updated_at; }
    public void setUpdated_at(LocalDateTime updated_at) { this.updated_at = updated_at; }
    public LocalDateTime getUltimoAcesso() { return ultimoAcesso; }
    public void setUltimoAcesso(LocalDateTime ultimoAcesso) { this.ultimoAcesso = ultimoAcesso; }

    public static UserResponseBuilder builder() { return new UserResponseBuilder(); }

    public static class UserResponseBuilder {
        private Long id;
        private String username;
        private String email;
        private String nomeCompleto;
        private String telefone;
        private Set<Role> roles;
        private Boolean ativo;
        private LocalDateTime created_at;
        private LocalDateTime updated_at;
        private LocalDateTime ultimoAcesso;

        public UserResponseBuilder id(Long id) { this.id = id; return this; }
        public UserResponseBuilder username(String username) { this.username = username; return this; }
        public UserResponseBuilder email(String email) { this.email = email; return this; }
        public UserResponseBuilder nomeCompleto(String nomeCompleto) { this.nomeCompleto = nomeCompleto; return this; }
        public UserResponseBuilder telefone(String telefone) { this.telefone = telefone; return this; }
        public UserResponseBuilder roles(Set<Role> roles) { this.roles = roles; return this; }
        public UserResponseBuilder ativo(Boolean ativo) { this.ativo = ativo; return this; }
        public UserResponseBuilder created_at(LocalDateTime created_at) { this.created_at = created_at; return this; }
        public UserResponseBuilder updated_at(LocalDateTime updated_at) { this.updated_at = updated_at; return this; }
        public UserResponseBuilder ultimoAcesso(LocalDateTime ultimoAcesso) { this.ultimoAcesso = ultimoAcesso; return this; }

        public UserResponse build() {
            return new UserResponse(id, username, email, nomeCompleto, telefone, roles, ativo, created_at, updated_at, ultimoAcesso);
        }
    }
}
