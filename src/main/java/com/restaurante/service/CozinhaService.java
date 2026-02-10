package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service para operações de negócio com Cozinha
 * 
 * Cozinha é o recurso operacional de preparação
 * Responsável por preparar itens de pedido baseado em sua especialização
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CozinhaService {

    private final CozinhaRepository cozinhaRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    /**
     * Cria nova cozinha
     */
    @Transactional
    public Cozinha criar(String nome, TipoCozinha tipo, String idImpressora) {
        log.info("Criando cozinha: {} - Tipo: {}", nome, tipo);

        // Valida se já existe cozinha com mesmo nome
        cozinhaRepository.findByNomeIgnoreCase(nome).ifPresent(c -> {
            throw new BusinessException("Já existe uma cozinha com o nome: " + nome);
        });

        Cozinha cozinha = Cozinha.builder()
                .nome(nome)
                .tipo(tipo)
                .impressoraId(idImpressora)
                .ativa(true)
                .build();

        Cozinha cozinhaSalva = cozinhaRepository.save(cozinha);
        log.info("Cozinha criada com sucesso - ID: {}", cozinhaSalva.getId());
        
        return cozinhaSalva;
    }

    /**
     * Busca cozinha por ID
     */
    @Transactional(readOnly = true)
    public Cozinha buscarPorId(Long id) {
        return cozinhaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cozinha não encontrada"));
    }

    /**
     * Lista todas as cozinhas
     */
    @Transactional(readOnly = true)
    public List<Cozinha> listarTodas() {
        return cozinhaRepository.findAll();
    }

    /**
     * Lista cozinhas ativas
     */
    @Transactional(readOnly = true)
    public List<Cozinha> listarAtivas() {
        return cozinhaRepository.findByAtivaTrue();
    }

    /**
     * Lista cozinhas por tipo
     */
    @Transactional(readOnly = true)
    public List<Cozinha> listarPorTipo(TipoCozinha tipo) {
        return cozinhaRepository.findByTipo(tipo);
    }

    /**
     * Ativa uma cozinha
     */
    @Transactional
    public Cozinha ativar(Long id) {
        log.info("Ativando cozinha ID: {}", id);
        
        Cozinha cozinha = buscarPorId(id);
        cozinha.setAtiva(true);
        
        return cozinhaRepository.save(cozinha);
    }

    /**
     * Desativa uma cozinha
     */
    @Transactional
    public Cozinha desativar(Long id) {
        log.info("Desativando cozinha ID: {}", id);
        
        Cozinha cozinha = buscarPorId(id);
        
        // Verifica se há SubPedidos ativos
        long subPedidosAtivos = cozinhaRepository.contarSubPedidosAtivosPorCozinha(id);
        if (subPedidosAtivos > 0) {
            throw new BusinessException(
                "Não é possível desativar cozinha com " + subPedidosAtivos + " SubPedidos ativos. Finalize os pedidos primeiro."
            );
        }
        
        cozinha.setAtiva(false);
        
        return cozinhaRepository.save(cozinha);
    }

    /**
     * Vincula cozinha a uma unidade de atendimento
     */
    @Transactional
    public void vincularUnidadeAtendimento(Long cozinhaId, Long unidadeAtendimentoId) {
        log.info("Vinculando cozinha {} à unidade de atendimento {}", cozinhaId, unidadeAtendimentoId);
        
        Cozinha cozinha = buscarPorId(cozinhaId);
        UnidadeAtendimento unidade = unidadeAtendimentoRepository.findById(unidadeAtendimentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de atendimento não encontrada"));
        
        // Adiciona relacionamento bidirecional
        if (!cozinha.getUnidadesAtendimento().contains(unidade)) {
            cozinha.getUnidadesAtendimento().add(unidade);
            unidade.getCozinhas().add(cozinha);
            
            cozinhaRepository.save(cozinha);
            log.info("Vínculo criado com sucesso");
        } else {
            log.info("Vínculo já existe");
        }
    }

    /**
     * Remove vínculo entre cozinha e unidade de atendimento
     */
    @Transactional
    public void desvincularUnidadeAtendimento(Long cozinhaId, Long unidadeAtendimentoId) {
        log.info("Desvinculando cozinha {} da unidade de atendimento {}", cozinhaId, unidadeAtendimentoId);
        
        Cozinha cozinha = buscarPorId(cozinhaId);
        UnidadeAtendimento unidade = unidadeAtendimentoRepository.findById(unidadeAtendimentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de atendimento não encontrada"));
        
        cozinha.getUnidadesAtendimento().remove(unidade);
        unidade.getCozinhas().remove(cozinha);
        
        cozinhaRepository.save(cozinha);
        log.info("Vínculo removido com sucesso");
    }

    /**
     * Atualiza ID da impressora
     */
    @Transactional
    public Cozinha atualizarImpressora(Long id, String idImpressora) {
        log.info("Atualizando impressora da cozinha ID: {} para {}", id, idImpressora);
        
        Cozinha cozinha = buscarPorId(id);
        cozinha.setImpressoraId(idImpressora);
        
        return cozinhaRepository.save(cozinha);
    }

    /**
     * Busca cozinhas de uma unidade de atendimento
     */
    @Transactional(readOnly = true)
    public List<Cozinha> buscarPorUnidadeAtendimento(Long unidadeAtendimentoId) {
        return cozinhaRepository.findByUnidadeAtendimentoId(unidadeAtendimentoId);
    }

    /**
     * Conta SubPedidos ativos de uma cozinha
     */
    @Transactional(readOnly = true)
    public long contarSubPedidosAtivos(Long cozinhaId) {
        return cozinhaRepository.contarSubPedidosAtivosPorCozinha(cozinhaId);
    }
}
