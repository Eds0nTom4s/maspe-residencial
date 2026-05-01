package com.restaurante.store.security;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Cliente;
import com.restaurante.model.entity.SocioVinculo;
import com.restaurante.model.enums.TipoUsuario;
import com.restaurante.repository.ClienteRepository;
import com.restaurante.repository.SocioVinculoRepository;
import com.restaurante.store.dto.StoreSocioIdentityDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class StoreSocioIdentityResolver {

    private final AssociagestIdentityPort identityPort;
    private final SocioVinculoRepository socioVinculoRepository;
    private final ClienteRepository clienteRepository;

    public StoreSocioIdentityResolver(AssociagestIdentityPort identityPort,
                                      SocioVinculoRepository socioVinculoRepository,
                                      ClienteRepository clienteRepository) {
        this.identityPort = identityPort;
        this.socioVinculoRepository = socioVinculoRepository;
        this.clienteRepository = clienteRepository;
    }

    @Transactional
    public StoreSocioIdentityDTO resolve(HttpServletRequest request) {
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            throw new BusinessException("Token de sócio é obrigatório");
        }
        StoreSocioIdentityDTO identity = identityPort.validate(token);
        ensureMapping(identity);
        return identity;
    }

    @Transactional
    public StoreSocioIdentityDTO resolveOptional(HttpServletRequest request) {
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            return null;
        }
        StoreSocioIdentityDTO identity = identityPort.validate(token);
        identity.setSocio(true);
        ensureMapping(identity);
        return identity;
    }

    public String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        String xToken = request.getHeader("X-Socio-Token");
        return StringUtils.hasText(xToken) ? xToken : null;
    }

    public Cliente ensureMapping(StoreSocioIdentityDTO identity) {
        if (!identity.isSocio()) {
            return clienteRepository.findByTelefone(identity.getPhone())
                    .map(cliente -> {
                        if (StringUtils.hasText(identity.getName()) && !identity.getName().equals(cliente.getNome())) {
                            cliente.setNome(identity.getName());
                            return clienteRepository.save(cliente);
                        }
                        return cliente;
                    })
                    .orElseGet(() -> clienteRepository.save(Cliente.builder()
                            .telefone(identity.getPhone())
                            .nome(identity.getName())
                            .telefoneVerificado(false)
                            .tipoUsuario(TipoUsuario.CLIENTE)
                            .ativo(true)
                            .build()));
        }

        SocioVinculo vinculo = socioVinculoRepository.findBySocioId(identity.getSocioId())
                .orElseGet(() -> SocioVinculo.builder()
                        .socioId(identity.getSocioId())
                        .nome(identity.getName())
                        .telefone(identity.getPhone())
                        .email(identity.getEmail())
                        .build());
        vinculo.actualizarDados(identity.getName(), identity.getEmail());
        socioVinculoRepository.save(vinculo);

        return clienteRepository.findByTelefone(identity.getPhone())
                .map(cliente -> {
                    if (StringUtils.hasText(identity.getName()) && !identity.getName().equals(cliente.getNome())) {
                        cliente.setNome(identity.getName());
                        return clienteRepository.save(cliente);
                    }
                    return cliente;
                })
                .orElseGet(() -> clienteRepository.save(Cliente.builder()
                        .telefone(identity.getPhone())
                        .nome(identity.getName())
                        .telefoneVerificado(true)
                        .tipoUsuario(TipoUsuario.CLIENTE)
                        .ativo(true)
                        .build()));
    }
}
