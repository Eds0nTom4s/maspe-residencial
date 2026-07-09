package com.restaurante.dto.request;

import com.restaurante.model.enums.Role;
import jakarta.validation.constraints.Email;

import java.util.Set;

/**
 * Request para actualização de dados de utilizador (uso exclusivo ADMIN).
 * Todos os campos são opcionais — apenas os não-nulos são actualizados.
 */
public class AtualizarUsuarioRequest {

    @Email(message = "Email inválido")
    private String email;

    private String nomeCompleto;
    private String telefone;
    private Long unidadeAtendimentoId;
    private Set<Role> roles;

    public AtualizarUsuarioRequest() {}

    public AtualizarUsuarioRequest(String email, String nomeCompleto, String telefone, Long unidadeAtendimentoId, Set<Role> roles) {
        this.email = email;
        this.nomeCompleto = nomeCompleto;
        this.telefone = telefone;
        this.unidadeAtendimentoId = unidadeAtendimentoId;
        this.roles = roles;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNomeCompleto() { return nomeCompleto; }
    public void setNomeCompleto(String nomeCompleto) { this.nomeCompleto = nomeCompleto; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public Long getUnidadeAtendimentoId() { return unidadeAtendimentoId; }
    public void setUnidadeAtendimentoId(Long unidadeAtendimentoId) { this.unidadeAtendimentoId = unidadeAtendimentoId; }
    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
}
