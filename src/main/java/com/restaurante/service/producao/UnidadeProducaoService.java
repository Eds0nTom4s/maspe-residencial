package com.restaurante.service.producao;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
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
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UnidadeProducaoService {

    public static final String CODIGO_DEFAULT_GERAL = "GERAL";

    private final UnidadeProducaoRepository unidadeProducaoRepository;
    private final TenantRepository tenantRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final com.restaurante.repository.RotaProducaoCategoriaRepository rotaProducaoCategoriaRepository;

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
            if (!Boolean.TRUE.equals(existing.get().getAtivo())) {
                throw new ConflictException("A unidade de produção GERAL está desactivada.");
            }
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
    public List<UnidadeProducao> listarDoTenant(Long tenantId) {
        return unidadeProducaoRepository.findByTenantIdOrderByOrdemAscNomeAsc(tenantId);
    }

    @Transactional
    public UnidadeProducao criar(Long tenantId, Long instituicaoId, Long unidadeAtendimentoId,
                                 String nome, String codigo, UnidadeProducaoTipo tipo, Integer ordem) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        Instituicao instituicao = instituicaoRepository.findById(instituicaoId)
                .filter(item -> item.getTenant() != null && tenantId.equals(item.getTenant().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        UnidadeAtendimento unidadeAtendimento = resolveUnidadeAtendimento(instituicao, unidadeAtendimentoId);
        String codigoNormalizado = normalizeCode(codigo);
        if (unidadeProducaoRepository.existsByTenantIdAndInstituicaoIdAndCodigo(
                tenantId, instituicaoId, codigoNormalizado)) {
            throw new ConflictException("Já existe uma unidade de produção com este código na instituição.");
        }
        UnidadeProducao unidade = new UnidadeProducao();
        unidade.setTenant(tenant);
        unidade.setInstituicao(instituicao);
        unidade.setUnidadeAtendimento(unidadeAtendimento);
        unidade.setNome(requireName(nome));
        unidade.setCodigo(codigoNormalizado);
        unidade.setTipo(tipo != null ? tipo : UnidadeProducaoTipo.OUTRO);
        unidade.setAtivo(true);
        unidade.setOrdem(ordem != null ? ordem : 0);
        return unidadeProducaoRepository.save(unidade);
    }

    @Transactional
    public UnidadeProducao atualizar(Long tenantId, Long id, boolean atualizarUnidadeAtendimento,
                                     Long unidadeAtendimentoId,
                                     String nome, UnidadeProducaoTipo tipo, Integer ordem) {
        UnidadeProducao unidade = buscarPorIdETenant(id, tenantId);
        if (nome != null) unidade.setNome(requireName(nome));
        if (tipo != null) unidade.setTipo(tipo);
        if (ordem != null) unidade.setOrdem(ordem);
        if (atualizarUnidadeAtendimento) {
            unidade.setUnidadeAtendimento(resolveUnidadeAtendimento(
                    unidade.getInstituicao(), unidadeAtendimentoId));
        }
        unidade.setAtualizadoEm(java.time.LocalDateTime.now());
        return unidadeProducaoRepository.save(unidade);
    }

    @Transactional
    public UnidadeProducao ativar(Long tenantId, Long id) {
        UnidadeProducao unidade = buscarPorIdETenant(id, tenantId);
        unidade.setAtivo(true);
        unidade.setAtualizadoEm(java.time.LocalDateTime.now());
        return unidadeProducaoRepository.save(unidade);
    }

    @Transactional
    public UnidadeProducao desativar(Long tenantId, Long id) {
        UnidadeProducao unidade = buscarPorIdETenant(id, tenantId);
        if (CODIGO_DEFAULT_GERAL.equals(unidade.getCodigo())) {
            throw new ConflictException("A unidade GERAL é obrigatória e não pode ser desactivada.");
        }
        if (rotaProducaoCategoriaRepository.existsByTenantIdAndUnidadeProducaoIdAndAtivoTrue(tenantId, id)) {
            throw new ConflictException("A unidade possui rotas activas. Reatribua ou desactive as rotas antes de continuar.");
        }
        unidade.setAtivo(false);
        unidade.setAtualizadoEm(java.time.LocalDateTime.now());
        return unidadeProducaoRepository.save(unidade);
    }

    @Transactional(readOnly = true)
    public UnidadeProducao buscarPorIdETenant(Long id, Long tenantId) {
        return unidadeProducaoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("UnidadeProducao", "id", id));
    }

    @Transactional
    public UnidadeProducao obterDefaultParaInstituicao(Long tenantId, Long instituicaoId) {
        return unidadeProducaoRepository.findByTenantIdAndInstituicaoIdAndCodigo(tenantId, instituicaoId, CODIGO_DEFAULT_GERAL)
                .map(unidade -> {
                    if (!Boolean.TRUE.equals(unidade.getAtivo())) {
                        throw new ConflictException("A unidade de produção GERAL está desactivada.");
                    }
                    return unidade;
                })
                .orElseGet(() -> criarDefaultGeral(tenantId, instituicaoId, null, "Produção Geral", UnidadeProducaoTipo.OUTRO));
    }

    private UnidadeAtendimento resolveUnidadeAtendimento(Instituicao instituicao, Long unidadeAtendimentoId) {
        if (unidadeAtendimentoId == null) return null;
        return unidadeAtendimentoRepository.findById(unidadeAtendimentoId)
                .filter(item -> item.getInstituicao() != null
                        && item.getInstituicao().getId().equals(instituicao.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
    }

    private String normalizeCode(String codigo) {
        if (codigo == null || codigo.isBlank()) throw new BusinessException("Código da unidade é obrigatório.");
        String normalized = codigo.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_-]{1,40}")) {
            throw new BusinessException("Código da unidade inválido.");
        }
        return normalized;
    }

    private String requireName(String nome) {
        if (nome == null || nome.isBlank()) throw new BusinessException("Nome da unidade é obrigatório.");
        return nome.trim();
    }
}
