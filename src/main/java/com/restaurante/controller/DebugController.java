package com.restaurante.controller;

import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller de debug para suporte em ambientes não-produtivos.
 *
 * <p>Apenas acessível com perfil ativo diferente de "prod" e por utilizadores ADMIN.
 * Nunca deve ser deployado sem estas restrições.
 */
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
@Profile("!prod")
@PreAuthorize("hasRole('ADMIN')")
public class DebugController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/users")
    public List<Map<String, Object>> listarUsuarios() {
        return userRepository.findAll().stream()
                .map(user -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", user.getId());
                    map.put("username", user.getUsername());
                    map.put("telefone", user.getTelefone());
                    map.put("email", user.getEmail());
                    map.put("roles", user.getRoles());
                    map.put("ativo", user.getAtivo());
                    // passwordHash removido: nunca expor hashes em respostas HTTP
                    return map;
                })
                .collect(Collectors.toList());
    }

    @PostMapping("/create-admin")
    public Map<String, Object> criarAdmin() {
        Map<String, Object> result = new HashMap<>();
        
        if (userRepository.findByUsername("admin").isPresent()) {
            result.put("status", "exists");
            result.put("message", "Usuário admin já existe");
            return result;
        }
        
        User admin = User.builder()
            .username("admin")
            .password(passwordEncoder.encode("admin123"))
            .nomeCompleto("Administrador do Sistema")
            .telefone("+244923456789")
            .email("admin@restaurante.ao")
            .roles(Set.of(Role.ROLE_ADMIN))
            .ativo(true)
            .build();
        
        userRepository.save(admin);
        
        result.put("status", "created");
        result.put("message", "Admin criado com sucesso");
        result.put("username", "admin");
        result.put("telefone", "+244923456789");
        // senha em texto simples removida da resposta HTTP
        return result;
    }
}
