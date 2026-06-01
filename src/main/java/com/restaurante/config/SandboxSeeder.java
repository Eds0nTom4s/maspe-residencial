package com.restaurante.config;

import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Seeder mínimo para SANDBOX operacional controlada.
 *
 * <p>Objetivo: garantir que exista um utilizador PLATFORM_ADMIN (ROLE_ADMIN)
 * para login JWT e execução de endpoints /api/platform/** (templates/provisioning),
 * sem depender do seeder de DEV.</p>
 *
 * <p>Configuração via env vars (Spring relaxed binding):
 * <ul>
 *   <li>SANDBOX_ADMIN_USERNAME -> sandbox.admin.username</li>
 *   <li>SANDBOX_ADMIN_PASSWORD -> sandbox.admin.password</li>
 * </ul>
 * </p>
 */
@Component
@Profile("sandbox")
@Order(90)
@RequiredArgsConstructor
public class SandboxSeeder {

    private static final Logger log = LoggerFactory.getLogger(SandboxSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${sandbox.admin.username:admin}")
    private String username;

    @Value("${sandbox.admin.password:}")
    private String password;

    @Value("${sandbox.admin.email:admin@sandbox.local}")
    private String email;

    @Value("${sandbox.admin.telefone:+244900000000}")
    private String telefone;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedAdminIfMissing() {
        boolean alreadyExists = userRepository.findByUsername(username).isPresent();
        if (alreadyExists) {
            log.info("[sandbox] admin user '{}' já existe — ok", username);
            return;
        }

        String pwd = password != null ? password.trim() : "";
        if (pwd.isBlank()) {
            throw new IllegalStateException("SANDBOX_ADMIN_PASSWORD (sandbox.admin.password) não configurado. " +
                    "Defina um password forte em .env.sandbox antes de subir a sandbox.");
        }

        if (pwd.length() < 12) {
            log.warn("[sandbox] SANDBOX_ADMIN_PASSWORD fraco (<12 chars). Usar secret forte, especialmente em servidor.");
        }

        User admin = User.builder()
                .username(username)
                .password(passwordEncoder.encode(pwd))
                .nomeCompleto("Administrador Sandbox")
                .telefone(telefone)
                .email(email)
                .roles(Set.of(Role.ROLE_ADMIN))
                .ativo(true)
                .build();

        userRepository.save(admin);
        log.info("[sandbox] ✅ admin user criado: username='{}' roles={}", username, admin.getRoles());
    }
}

