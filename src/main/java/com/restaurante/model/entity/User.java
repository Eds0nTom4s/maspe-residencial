package com.restaurante.model.entity;

import com.restaurante.model.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
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
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @Column(name = "ativo", nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @Column(name = "ultimo_acesso")
    private LocalDateTime ultimoAcesso;

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
