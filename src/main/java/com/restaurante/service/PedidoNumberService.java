package com.restaurante.service;

import com.restaurante.model.entity.PedidoSequenceCounter;
import com.restaurante.repository.PedidoSequenceCounterRepository;
import com.restaurante.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class PedidoNumberService {

    private final PedidoSequenceCounterRepository pedidoSequenceCounterRepository;
    private final TenantRepository tenantRepository;

    /**
     * Gera número concorrência-safe por tenant e por data (sequência diária).
     *
     * Formato: PED-{tenantCode}-{yyyyMMdd}-{seq6}
     */
    @Transactional
    public String gerarNumeroPedido(Long tenantId) {
        String tenantCode = tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant não encontrado: " + tenantId))
                .getTenantCode();

        LocalDate hoje = LocalDate.now();
        PedidoSequenceCounter counter = pedidoSequenceCounterRepository.findForUpdateByTenantIdAndDataReferencia(tenantId, hoje)
                .orElseGet(() -> {
                    PedidoSequenceCounter c = new PedidoSequenceCounter();
                    c.setTenant(tenantRepository.getReferenceById(tenantId));
                    c.setDataReferencia(hoje);
                    c.setProximoNumero(1L);
                    return pedidoSequenceCounterRepository.saveAndFlush(c);
                });

        long seq = counter.getProximoNumero() != null ? counter.getProximoNumero() : 1L;
        counter.setProximoNumero(seq + 1);
        pedidoSequenceCounterRepository.save(counter);

        String data = hoje.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("PED-%s-%s-%06d", tenantCode, data, seq);
    }
}
