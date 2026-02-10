package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service para operações de negócio com UnidadeAtendimento
 * 
 * UnidadeAtendimento é o ponto de entrada do pedido no sistema
 * Coordena atendimento e vincula cozinhas responsáveis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnidadeAtendimentoService {

    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final CozinhaRepository cozinhaRepository;

    /**
     * Cria nova unidade de atendimento
     */
    @Transactional
    public UnidadeAtendimento criar(String nome, TipoUnidadeAtendimento tipo, String descricao) {
        log.info("Criando unidade de atendimento: {} - Tipo: {}", nome, tipo);

        // Valida se já existe unidade com mesmo nome
        unidadeAtendimentoRepository.findByNomeIgnoreCase(nome).ifPresent(u -> {
            throw new BusinessException("Já existe uma unidade de atendimento com o nome: " + nome);
        });

        UnidadeAtendimento unidade = UnidadeAtendimento.builder()
                .nome(nome)
                .tipo(tipo)
                .descricao(descricao)
                .ativa(true)
                .build();

        UnidadeAtendimento unidadeSalva = unidadeAtendimentoRepository.save(unidade);
        log.info("Unidade de atendimento criada com sucesso - ID: {}", unidadeSalva.getId());
        
        return unidadeSalva;
    }

    /**
     * Busca unidade por ID
     */
    @Transactional(readOnly = true)
    public UnidadeAtendimento buscarPorId(Long id) {
        return unidadeAtendimentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de atendimento não encontrada"));
    }

    /**
     * Lista todas as unidades
     */
    @Transactional(readOnly = true)
    public List<UnidadeAtendimento> listarTodas() {
        return unidadeAtendimentoRepository.findAll();
    }

    /**
     * Lista unidades ativas
     */
    @Transactional(readOnly = true)
    public List<UnidadeAtendimento> listarAtivas() {
        return unidadeAtendimentoRepository.findByAtivaTrue();
    }

    /**
     * Lista unidades operacionais (ativas com cozinhas)
     */
    @Transactional(readOnly = true)
    public List<UnidadeAtendimento> listarOperacionais() {
        return unidadeAtendimentoRepository.findUnidadesOperacionais();
    }

    /**
     * Lista unidades por tipo
     */
    @Transactional(readOnly = true)
    public List<UnidadeAtendimento> listarPorTipo(TipoUnidadeAtendimento tipo) {
        return unidadeAtendimentoRepository.findByTipo(tipo);
    }

    /**
     * Ativa uma unidade
     */
    @Transactional
    public UnidadeAtendimento ativar(Long id) {
        log.info("Ativando unidade de atendimento ID: {}", id);
        
        UnidadeAtendimento unidade = buscarPorId(id);
        unidade.setAtiva(true);
        
        return unidadeAtendimentoRepository.save(unidade);
    }

    /**
     * Desativa uma unidade
     */
    @Transactional
    public UnidadeAtendimento desativar(Long id) {
        log.info("Desativando unidade de atendimento ID: {}", id);
        
        UnidadeAtendimento unidade = buscarPorId(id);
        
        // Verifica se há unidades de consumo ativas
        long unidadesConsumoAtivas = unidadeAtendimentoRepository.contarUnidadesConsumoAtivasPorUnidade(id);
        if (unidadesConsumoAtivas > 0) {
            throw new BusinessException(
                "Não é possível desativar unidade com " + unidadesConsumoAtivas + " unidades de consumo ativas"
            );
        }
        
        unidade.setAtiva(false);
        
        return unidadeAtendimentoRepository.save(unidade);
    }

    /**
     * Adiciona cozinha à unidade
     */
    @Transactional
    public void adicionarCozinha(Long unidadeId, Long cozinhaId) {
        log.info("Adicionando cozinha {} à unidade {}", cozinhaId, unidadeId);
        
        UnidadeAtendimento unidade = buscarPorId(unidadeId);
        Cozinha cozinha = cozinhaRepository.findById(cozinhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cozinha não encontrada"));
        
        unidade.adicionarCozinha(cozinha);
        unidadeAtendimentoRepository.save(unidade);
        
        log.info("Cozinha adicionada com sucesso");
    }

    /**
     * Remove cozinha da unidade
     */
    @Transactional
    public void removerCozinha(Long unidadeId, Long cozinhaId) {
        log.info("Removendo cozinha {} da unidade {}", cozinhaId, unidadeId);
        
        UnidadeAtendimento unidade = buscarPorId(unidadeId);
        Cozinha cozinha = cozinhaRepository.findById(cozinhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cozinha não encontrada"));
        
        unidade.removerCozinha(cozinha);
        unidadeAtendimentoRepository.save(unidade);
        
        log.info("Cozinha removida com sucesso");
    }

    /**
     * Verifica se unidade está operacional
     */
    @Transactional(readOnly = true)
    public boolean isOperacional(Long id) {
        UnidadeAtendimento unidade = buscarPorId(id);
        return unidade.isOperacional();
    }

    /**
     * Busca cozinhas de uma unidade
     */
    @Transactional(readOnly = true)
    public List<Cozinha> buscarCozinhas(Long unidadeId) {
        UnidadeAtendimento unidade = buscarPorId(unidadeId);
        return unidade.getCozinhas();
    }

    /**
     * Conta unidades de consumo ativas
     */
    @Transactional(readOnly = true)
    public long contarUnidadesConsumoAtivas(Long unidadeId) {
        return unidadeAtendimentoRepository.contarUnidadesConsumoAtivasPorUnidade(unidadeId);
    }
}
