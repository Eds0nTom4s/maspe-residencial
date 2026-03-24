package com.restaurante.dto.request;

import com.restaurante.model.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

import java.util.Set;
public class CriarUsuarioRequest {

    @NotBlank(message = "Username é obrigatório")
    @Size(min = 3, max = 50, message = "Username deve ter entre 3 e 50 caracteres")
    private String username;

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
    private String senha;

    @Email(message = "Email inválido")
    private String email;

    private String nomeCompleto;

    private String telefone;

    @NotEmpty(message = "Ao menos uma role é obrigatória")
    private Set<Role> roles;

    public CriarUsuarioRequest() {
    }

    public CriarUsuarioRequest(String username, String senha, String email, String nomeCompleto, String telefone, Set<Role> roles) {
        this.username = username;
        this.senha = senha;
        this.email = email;
        this.nomeCompleto = nomeCompleto;
        this.telefone = telefone;
        this.roles = roles;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNomeCompleto() { return nomeCompleto; }
    public void setNomeCompleto(String nomeCompleto) { this.nomeCompleto = nomeCompleto; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }

    public static CriarUsuarioRequestBuilder builder() {
        return new CriarUsuarioRequestBuilder();
    }

    public static class CriarUsuarioRequestBuilder {
        private String username;
        private String senha;
        private String email;
        private String nomeCompleto;
        private String telefone;
        private Set<Role> roles;

        public CriarUsuarioRequestBuilder username(String username) { this.username = username; return this; }
        public CriarUsuarioRequestBuilder senha(String senha) { this.senha = senha; return this; }
        public CriarUsuarioRequestBuilder email(String email) { this.email = email; return this; }
        public CriarUsuarioRequestBuilder nomeCompleto(String nomeCompleto) { this.nomeCompleto = nomeCompleto; return this; }
        public CriarUsuarioRequestBuilder telefone(String telefone) { this.telefone = telefone; return this; }
        public CriarUsuarioRequestBuilder roles(Set<Role> roles) { this.roles = roles; return this; }

        public CriarUsuarioRequest build() {
            return new CriarUsuarioRequest(username, senha, email, nomeCompleto, telefone, roles);
        }
    }
}
