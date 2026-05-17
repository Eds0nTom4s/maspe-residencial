package com.restaurante.service.producao;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.UnidadeProducao;
import com.restaurante.model.enums.UnidadeProducaoTipo;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UnidadeProducaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UnidadeProducaoService {

    public static final String CODIGO_DEFAULT_GERAL = "GERAL";

    private final UnidadeProducaoRepository unidadeProducaoRepository;
    private final TenantRepository tenantRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    @Transactional
    public UnidadeProducao criarDefaultGeral(Long tenantId, Long instituicaoId, Long unidadeAtendimentoId, String nome, UnidadeProducaoTipo tipo) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));
        Instituicao inst = instituicaoRepository.findById(instituicaoId).orElseThrow(() -> new ResourceNotFoundException("Instituicao", "id", instituicaoId));
        if (inst.getTenant() == null || !inst.getTenant().getId().equals(tenant.getId())) {
            throw new ResourceNotFoundException("Instituicao", "id", instituicaoId);
        }

        UnidadeAtendimento ua = null;
        if (unidadeAtendimentoId != null) {
            ua = unidadeAtendimentoRepository.findById(unidadeAtendimentoId)
                    .orElseThrow(() -> new ResourceNotFoundException("UnidadeAtendimento", "id", unidadeAtendimentoId));
            if (ua.getInstituicao() == null || !ua.getInstituicao().getId().equals(inst.getId())) {
                throw new ResourceNotFoundException("UnidadeAtendimento", "id", unidadeAtendimentoId);
            }
        }

        Optional<UnidadeProducao> existing = unidadeProducaoRepository.findByTenantIdAndInstituicaoIdAndCodigo(tenant.getId(), inst.getId(), CODIGO_DEFAULT_GERAL);
        if (existing.isPresent()) {
            return existing.get();
        }

        UnidadeProducao up = new UnidadeProducao();
        up.setTenant(tenant);
        up.setInstituicao(inst);
        up.setUnidadeAtendimento(ua);
        up.setNome(nome != null && !nome.isBlank() ? nome : "Produção Geral");
        up.setCodigo(CODIGO_DEFAULT_GERAL);
        up.setTipo(tipo != null ? tipo : UnidadeProducaoTipo.OUTRO);
        up.setAtivo(true);
        up.setOrdem(0);
        return unidadeProducaoRepository.save(up);
    }

    @Transactional(readOnly = true)
    public List<UnidadeProducao> listarAtivasDoTenant(Long tenantId) {
        return unidadeProducaoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public UnidadeProducao buscarPorIdETenant(Long id, Long tenantId) {
        return unidadeProducaoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("UnidadeProducao", "id", id));
    }

    @Transactional(readOnly = true)
    public UnidadeProducao obterDefaultParaInstituicao(Long tenantId, Long instituicaoId) {
        return unidadeProducaoRepository.findByTenantIdAndInstituicaoIdAndCodigo(tenantId, instituicaoId, CODIGO_DEFAULT_GERAL)
                .orElseGet(() -> criarDefaultGeral(tenantId, instituicaoId, null, "Produção Geral", UnidadeProducaoTipo.OUTRO));
    }
}
