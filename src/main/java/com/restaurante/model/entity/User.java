package com.restaurante.model.entity;

import com.restaurante.model.enums.Role;
import jakarta.persistence.*;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entity User para autenticação e autorização
 * Implementa UserDetails do Spring Security
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_username", columnList = "username", unique = true),
    @Index(name = "idx_user_email", columnList = "email", unique = true)
})
public class User extends BaseEntity implements UserDetails {

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "nome_completo", length = 150)
    private String nomeCompleto;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Set<Role> roles = new HashSet<>();

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "ultimo_acesso")
    private LocalDateTime ultimoAcesso;

    public User() {}

    public User(String username, String password, String email, String nomeCompleto, String telefone, Set<Role> roles, Boolean ativo, LocalDateTime ultimoAcesso) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.nomeCompleto = nomeCompleto;
        this.telefone = telefone;
        this.roles = roles != null ? roles : new HashSet<>();
        this.ativo = ativo != null ? ativo : true;
        this.ultimoAcesso = ultimoAcesso;
    }

    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
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
    public LocalDateTime getUltimoAcesso() { return ultimoAcesso; }
    public void setUltimoAcesso(LocalDateTime ultimoAcesso) { this.ultimoAcesso = ultimoAcesso; }

    public static UserBuilder builder() {
        return new UserBuilder();
    }

    public static class UserBuilder {
        private String username;
        private String password;
        private String email;
        private String nomeCompleto;
        private String telefone;
        private Set<Role> roles;
        private Boolean ativo;
        private LocalDateTime ultimoAcesso;

        UserBuilder() {}

        public UserBuilder username(String username) {
            this.username = username;
            return this;
        }

        public UserBuilder password(String password) {
            this.password = password;
            return this;
        }

        public UserBuilder email(String email) {
            this.email = email;
            return this;
        }

        public UserBuilder nomeCompleto(String nomeCompleto) {
            this.nomeCompleto = nomeCompleto;
            return this;
        }

        public UserBuilder telefone(String telefone) {
            this.telefone = telefone;
            return this;
        }

        public UserBuilder roles(Set<Role> roles) {
            this.roles = roles;
            return this;
        }

        public UserBuilder ativo(Boolean ativo) {
            this.ativo = ativo;
            return this;
        }

        public UserBuilder ultimoAcesso(LocalDateTime ultimoAcesso) {
            this.ultimoAcesso = ultimoAcesso;
            return this;
        }

        public User build() {
            return new User(this.username, this.password, this.email, this.nomeCompleto, this.telefone, this.roles, this.ativo, this.ultimoAcesso);
        }
    }

    /**
     * Relacionamento opcional com Cliente
     */
    // @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    // private Cliente cliente;

    /**
     * Relacionamento opcional com Atendente
     * TODO: Implementar relacionamento bidirecional se necessário
     */
    // @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    // private Atendente atendente;

    // ========== UserDetails Implementation ==========

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .collect(Collectors.toList());
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return ativo;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ativo;
    }

    // ========== Helper Methods ==========

    /**
     * Adiciona role ao usuário
     */
    public void adicionarRole(Role role) {
        this.roles.add(role);
    }

    /**
     * Remove role do usuário
     */
    public void removerRole(Role role) {
        this.roles.remove(role);
    }

    /**
     * Verifica se usuário tem role específica
     */
    public boolean hasRole(Role role) {
        return this.roles.contains(role);
    }

    /**
     * Verifica se é admin
     */
    public boolean isAdmin() {
        return hasRole(Role.ROLE_ADMIN);
    }

    /**
     * Verifica se é gerente ou admin
     */
    public boolean isGerenteOuAdmin() {
        return hasRole(Role.ROLE_GERENTE) || hasRole(Role.ROLE_ADMIN);
    }

    /**
     * Atualiza último acesso
     */
    public void atualizarUltimoAcesso() {
        this.ultimoAcesso = LocalDateTime.now();
    }
}
