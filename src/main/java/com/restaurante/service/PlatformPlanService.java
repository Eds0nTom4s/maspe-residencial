package com.restaurante.service;

import com.restaurante.dto.response.PlatformPlanResponse;
import com.restaurante.model.entity.Plano;
import com.restaurante.repository.PlanoRepository;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformPlanService {

    private final TenantGuard tenantGuard;
    private final PlanoRepository planoRepository;

    @Transactional(readOnly = true)
    public List<PlatformPlanResponse> listar(Boolean ativo) {
        tenantGuard.assertPlatformAdmin();
        List<Plano> planos = ativo == null
                ? planoRepository.findAll(Sort.by(Sort.Direction.ASC, "codigo"))
                : Boolean.TRUE.equals(ativo)
                    ? planoRepository.findByAtivoTrue()
                    : planoRepository.findAll(Sort.by(Sort.Direction.ASC, "codigo"));
        return planos.stream().map(this::toResponse).toList();
    }

    private PlatformPlanResponse toResponse(Plano plano) {
        return PlatformPlanResponse.builder()
                .id(plano.getId())
                .codigo(plano.getCodigo())
                .nome(plano.getNome())
                .descricao(plano.getDescricao())
                .moeda("AOA")
                .ciclo("MONTHLY")
                .precoMensal(plano.getPrecoMensal())
                .ativo(plano.getAtivo())
                .maxInstituicoes(plano.getMaxInstituicoes())
                .maxUnidadesAtendimento(plano.getMaxUnidadesAtendimento())
                .maxProdutos(plano.getMaxProdutos())
                .maxCategorias(plano.getMaxCategorias())
                .maxUsuarios(plano.getMaxUsuarios())
                .maxQrCodes(plano.getMaxQrCodes())
                .maxDispositivos(plano.getMaxDispositivos())
                .permiteMultiInstituicao(plano.getPermiteMultiInstituicao())
                .permitePedidosQr(plano.getPermitePedidosQr())
                .permitePos(plano.getPermitePos())
                .permiteOffline(plano.getPermiteOffline())
                .build();
    }
}
