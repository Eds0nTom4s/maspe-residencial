package com.restaurante.consumo.identificacao.service;

import com.restaurante.consumo.identificacao.entity.ClienteConsumo;
import com.restaurante.consumo.identificacao.repository.ClienteConsumoRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.ClienteConsumoStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ClienteConsumoService {

    private final ClienteConsumoRepository repository;

    @Transactional
    public GetOrCreateResult getOrCreateByPhone(Tenant tenant, String rawPhone, String phoneNormalized) {
        ClienteConsumo existing = repository.findByTenant_IdAndTelefoneNormalizado(tenant.getId(), phoneNormalized).orElse(null);
        Instant now = Instant.now();

        if (existing != null) {
            if (existing.getStatus() == ClienteConsumoStatus.BLOCKED) throw new BusinessException("CLIENTE_CONSUMO_BLOCKED");
            existing.setTelefone(rawPhone);
            existing.setUltimoAcessoEm(now);
            return new GetOrCreateResult(repository.save(existing), false);
        }

        ClienteConsumo c = new ClienteConsumo();
        c.setTenant(tenant);
        c.setTelefone(rawPhone);
        c.setTelefoneNormalizado(phoneNormalized);
        c.setStatus(ClienteConsumoStatus.ACTIVE);
        c.setTelefoneVerificado(false);
        c.setUltimoAcessoEm(now);
        return new GetOrCreateResult(repository.save(c), true);
    }

    @Transactional
    public ClienteConsumo markPhoneVerified(ClienteConsumo cliente) {
        Instant now = Instant.now();
        if (!cliente.isTelefoneVerificado()) {
            cliente.setTelefoneVerificado(true);
            if (cliente.getPrimeiroVerificadoEm() == null) cliente.setPrimeiroVerificadoEm(now);
        }
        cliente.setUltimoVerificadoEm(now);
        cliente.setUltimoAcessoEm(now);
        return repository.save(cliente);
    }

    public record GetOrCreateResult(ClienteConsumo cliente, boolean created) {}
}
