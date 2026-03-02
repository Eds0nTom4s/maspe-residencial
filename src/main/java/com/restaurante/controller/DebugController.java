package com.restaurante.controller;

import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TEMPORÁRIO: Controller de debug para verificar autenticação
 * ⚠️ REMOVER EM PRODUÇÃO
 */
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
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
                    map.put("passwordHash", user.getPassword());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @PostMapping("/test-password")
    public Map<String, Object> testarSenha(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByTelefone(username))
                .orElse(null);
        
        Map<String, Object> result = new HashMap<>();
        if (user == null) {
            result.put("found", false);
            result.put("message", "Usuário não encontrado: " + username);
        } else {
            boolean matches = passwordEncoder.matches(password, user.getPassword());
            result.put("found", true);
            result.put("username", user.getUsername());
            result.put("telefone", user.getTelefone());
            result.put("passwordMatch", matches);
            result.put("passwordProvided", password);
            result.put("passwordHashStored", user.getPassword());
        }
        return result;
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
        result.put("password", "admin123");
        return result;
    }
}

