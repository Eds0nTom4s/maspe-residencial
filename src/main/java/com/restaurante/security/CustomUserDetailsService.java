package com.restaurante.security;

import com.restaurante.model.entity.Atendente;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.repository.AtendenteRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.util.PhoneNumberUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Optional;

/**
 * UserDetailsService customizado para carregar usuário do banco
 * Suporta busca por username (User) OU telefone (User/Atendente)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final AtendenteRepository atendenteRepository;
    private final com.restaurante.repository.ClienteRepository clienteRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrPhone) throws UsernameNotFoundException {
        log.info("🔍 CustomUserDetailsService.loadUserByUsername() - Buscando: {}", usernameOrPhone);
        
        // 1. Tentar buscar por username primeiro (tabela users)
        log.info("  ┣ Tentando buscar por USERNAME na tabela 'users': {}", usernameOrPhone);
        Optional<User> userOpt = userRepository.findByUsername(usernameOrPhone);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            log.info("  ┗ ✅ User ENCONTRADO: username={}, roles={}", user.getUsername(), user.getRoles());
            return user;
        }
        
        // 2. Se não encontrou e parece um telefone, buscar por telefone (tabela users)
        if (usernameOrPhone.startsWith("+") || usernameOrPhone.matches("^\\d+$")) {
            log.info("  ┣ Não encontrado por username. Tentando buscar por TELEFONE na tabela 'users': {}", usernameOrPhone);
            
            try {
                String normalizedPhone = PhoneNumberUtil.normalize(usernameOrPhone);
                log.info("  ┣ Telefone normalizado: {} -> {}", usernameOrPhone, normalizedPhone);
                userOpt = userRepository.findByTelefone(normalizedPhone);
                
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    log.info("  ┗ ✅ User ENCONTRADO por telefone: username={}, roles={}", user.getUsername(), user.getRoles());
                    return user;
                }
            } catch (IllegalArgumentException e) {
                log.warn("  ┣ Erro ao normalizar telefone: {}", e.getMessage());
            }
            
            // 3. Se ainda não encontrou, buscar na tabela atendentes
            log.info("  ┣ Não encontrado na tabela 'users'. Tentando buscar na tabela 'atendentes': {}", usernameOrPhone);
            String phoneToSearch = usernameOrPhone.startsWith("+") ? usernameOrPhone : PhoneNumberUtil.normalize(usernameOrPhone);
            
            Optional<Atendente> atendenteOpt = atendenteRepository.findByTelefoneAndAtivoTrue(phoneToSearch);
            if (atendenteOpt.isPresent()) {
                Atendente atendente = atendenteOpt.get();
                log.info("  ┗ ✅ Atendente ENCONTRADO: telefone={}, tipo={}", atendente.getTelefone(), atendente.getTipoUsuario());
                
                // Converte Atendente para UserDetails
                Role role = switch (atendente.getTipoUsuario()) {
                    case ADMIN -> Role.ROLE_ADMIN;
                    case GERENTE -> Role.ROLE_GERENTE;
                    case ATENDENTE -> Role.ROLE_ATENDENTE;
                    case CLIENTE -> Role.ROLE_CLIENTE; // Não deveria acontecer em atendentes, mas precisa cobrir todos os casos
                };
                
                return org.springframework.security.core.userdetails.User.builder()
                        .username(atendente.getTelefone())
                        .password(atendente.getSenha())
                        .authorities(Collections.singleton(new SimpleGrantedAuthority(role.name())))
                        .accountExpired(false)
                        .accountLocked(!atendente.getAtivo())
                        .credentialsExpired(false)
                        .disabled(!atendente.getAtivo())
                        .build();
            }

            // 4. Se ainda não encontrou, buscar na tabela clientes
            log.info("  ┣ Não encontrado na tabela 'atendentes'. Tentando buscar na tabela 'clientes': {}", phoneToSearch);
            Optional<com.restaurante.model.entity.Cliente> clienteOpt = clienteRepository.findByTelefoneAndAtivoTrue(phoneToSearch);
            if (clienteOpt.isPresent()) {
                com.restaurante.model.entity.Cliente cliente = clienteOpt.get();
                log.info("  ┗ ✅ Cliente ENCONTRADO: telefone={}", cliente.getTelefone());

                return org.springframework.security.core.userdetails.User.builder()
                        .username(cliente.getTelefone())
                        .password("") // Cliente não usa senha (OTP fallback)
                        .authorities(Collections.singleton(new SimpleGrantedAuthority(Role.ROLE_CLIENTE.name())))
                        .accountExpired(false)
                        .accountLocked(!cliente.getAtivo())
                        .credentialsExpired(false)
                        .disabled(!cliente.getAtivo())
                        .build();
            }
        }
        
        // 5. Se ainda não encontrou, lançar exceção
        log.error("  ✗ Usuário/Atendente/Cliente NÃO encontrado: {}", usernameOrPhone);
        throw new UsernameNotFoundException("Usuário não encontrado: " + usernameOrPhone);
    }

    /**
     * Carrega usuário por ID
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado com ID: " + id));
    }
}
