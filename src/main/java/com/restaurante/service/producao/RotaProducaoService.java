package com.restaurante.service.producao;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.RotaProducaoCategoria;
import com.restaurante.model.entity.UnidadeProducao;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.RotaProducaoCategoriaRepository;
import com.restaurante.repository.UnidadeProducaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RotaProducaoService {

    private final RotaProducaoCategoriaRepository rotaRepository;
    private final CategoriaProdutoRepository categoriaProdutoRepository;
    private final UnidadeProducaoRepository unidadeProducaoRepository;
    private final UnidadeProducaoService unidadeProducaoService;

    @Transactional(readOnly = true)
    public UnidadeProducao resolverUnidadeProducaoParaProduto(Long tenantId, Long instituicaoId, Produto produto) {
        if (produto == null || produto.getCategoriaProduto() == null) {
            throw new BusinessException("Produto inválido para roteamento de produção.");
        }
        return resolverUnidadeProducaoParaCategoria(tenantId, instituicaoId, produto.getCategoriaProduto().getId());
    }

    @Transactional(readOnly = true)
    public UnidadeProducao resolverUnidadeProducaoParaCategoria(Long tenantId, Long instituicaoId, Long categoriaProdutoId) {
        RotaProducaoCategoria rota = rotaRepository
                .findByTenantIdAndInstituicaoIdAndCategoriaProdutoIdAndAtivoTrue(
                        tenantId, instituicaoId, categoriaProdutoId)
                .orElse(null);
        if (rota != null) {
            UnidadeProducao up = rota.getUnidadeProducao();
            if (up == null || up.getTenant() == null || !up.getTenant().getId().equals(tenantId)
                    || up.getInstituicao() == null || !up.getInstituicao().getId().equals(instituicaoId)
                    || !Boolean.TRUE.equals(up.getAtivo())) {
                throw new BusinessException("Rota de produção inválida.");
            }
            return up;
        }
        return unidadeProducaoService.obterDefaultParaInstituicao(tenantId, instituicaoId);
    }

    @Transactional
    public RotaProducaoCategoria configurarRota(Long tenantId, Long categoriaProdutoId, Long unidadeProducaoId, Integer prioridade) {
        CategoriaProduto categoria = categoriaProdutoRepository.findByIdAndTenantId(categoriaProdutoId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("CategoriaProduto", "id", categoriaProdutoId));
        UnidadeProducao unidade = unidadeProducaoRepository.findByIdAndTenantId(unidadeProducaoId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("UnidadeProducao", "id", unidadeProducaoId));

        // coerência tenant
        if (unidade.getTenant() == null || !unidade.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Unidade de produção inválida.");
        }
        if (!Boolean.TRUE.equals(unidade.getAtivo())) {
            throw new ConflictException("Unidade de produção inactiva não pode receber rotas.");
        }
        if (unidade.getInstituicao() == null) {
            throw new BusinessException("Unidade de produção sem instituição válida.");
        }

        RotaProducaoCategoria rota = rotaRepository
                .findByTenantIdAndInstituicaoIdAndCategoriaProdutoIdAndAtivoTrue(
                        tenantId, unidade.getInstituicao().getId(), categoria.getId())
                .orElseGet(() -> {
                    RotaProducaoCategoria r = new RotaProducaoCategoria();
                    r.setTenant(categoria.getTenant());
                    r.setInstituicao(unidade.getInstituicao());
                    r.setCategoriaProduto(categoria);
                    return r;
                });

        rota.setUnidadeProducao(unidade);
        rota.setAtivo(true);
        rota.setPrioridade(prioridade != null ? prioridade : 0);
        rota.setAtualizadoEm(java.time.LocalDateTime.now());
        RotaProducaoCategoria saved = rotaRepository.saveAndFlush(rota);
        return rotaRepository.findByIdAndTenantId(saved.getId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("RotaProducaoCategoria", "id", saved.getId()));
    }

    @Transactional(readOnly = true)
    public List<RotaProducaoCategoria> listarRotasDoTenant(Long tenantId) {
        return rotaRepository.findByTenantId(tenantId);
    }

    @Transactional
    public RotaProducaoCategoria desativarRota(Long tenantId, Long rotaId) {
        RotaProducaoCategoria rota = rotaRepository.findByIdAndTenantId(rotaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("RotaProducaoCategoria", "id", rotaId));
        rota.setAtivo(false);
        rota.setAtualizadoEm(java.time.LocalDateTime.now());
        return rotaRepository.save(rota);
    }
}
