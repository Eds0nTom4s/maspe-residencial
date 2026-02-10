package com.restaurante.service;

import com.restaurante.dto.request.CriarPagamentoRequest;
import com.restaurante.dto.response.PagamentoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.UnidadeDeConsumo;
import com.restaurante.model.enums.StatusPagamento;
import com.restaurante.model.enums.StatusUnidadeConsumo;
import com.restaurante.repository.PagamentoRepository;
import com.restaurante.repository.UnidadeDeConsumoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service para operações de negócio com Pagamento
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PagamentoService {

    private final PagamentoRepository pagamentoRepository;
    private final UnidadeDeConsumoRepository unidadeDeConsumoRepository;
    private final UnidadeDeConsumoService unidadeDeConsumoService;

    /**
     * Cria um novo pagamento para uma unidade de consumo
     */
    @Transactional
    public PagamentoResponse criar(CriarPagamentoRequest request) {
        log.info("Criando pagamento para unidade de consumo ID: {}", request.getUnidadeConsumoId());

        UnidadeDeConsumo unidadeConsumo = unidadeDeConsumoRepository.findById(request.getUnidadeConsumoId())
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de consumo não encontrada"));

        if (unidadeConsumo.getStatus() != StatusUnidadeConsumo.AGUARDANDO_PAGAMENTO && 
            unidadeConsumo.getStatus() != StatusUnidadeConsumo.OCUPADA) {
            throw new BusinessException("Unidade de consumo não está disponível para pagamento. Status: " + unidadeConsumo.getStatus());
        }

        if (pagamentoRepository.existsByUnidadeConsumoId(unidadeConsumo.getId())) {
            throw new BusinessException("Já existe um pagamento para esta unidade de consumo");
        }

        Pagamento pagamento = Pagamento.builder()
                .unidadeConsumo(unidadeConsumo)
                .valor(request.getValor())
                .metodoPagamento(request.getMetodoPagamento())
                .observacoes(request.getObservacoes())
                .build();

        Pagamento pagamentoSalvo = pagamentoRepository.save(pagamento);

        // TODO: Se for pagamento digital, integrar com gateway
        // TODO: Se for PIX, gerar QR Code

        log.info("Pagamento criado com sucesso - ID: {}", pagamentoSalvo.getId());
        return converterParaResponse(pagamentoSalvo);
    }

    /**
     * Busca pagamento por ID
     */
    @Transactional(readOnly = true)
    public PagamentoResponse buscarPorId(Long id) {
        Pagamento pagamento = pagamentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado"));
        return converterParaResponse(pagamento);
    }

    /**
     * Busca pagamento de uma unidade de consumo
     */
    @Transactional(readOnly = true)
    public PagamentoResponse buscarPorUnidadeConsumoId(Long unidadeConsumoId) {
        log.info("Buscando pagamento da unidade de consumo ID: {}", unidadeConsumoId);

        Pagamento pagamento = pagamentoRepository.findByUnidadeConsumoId(unidadeConsumoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado para esta unidade de consumo"));

        return converterParaResponse(pagamento);
    }

    /**
     * Aprova um pagamento
     */
    @Transactional
    public PagamentoResponse aprovar(Long id) {
        log.info("Aprovando pagamento ID: {}", id);

        Pagamento pagamento = pagamentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado"));

        if (pagamento.getStatus() == StatusPagamento.APROVADO) {
            throw new BusinessException("Pagamento já foi aprovado");
        }

        pagamento.aprovar();
        Pagamento pagamentoSalvo = pagamentoRepository.save(pagamento);

        // Atualiza status da unidade de consumo
        unidadeDeConsumoService.atualizarStatus(pagamento.getUnidadeConsumo().getId());

        log.info("Pagamento aprovado com sucesso - ID: {}", pagamentoSalvo.getId());
        return converterParaResponse(pagamentoSalvo);
    }

    /**
     * Recusa um pagamento
     */
    @Transactional
    public PagamentoResponse recusar(Long id, String motivo) {
        log.info("Recusando pagamento ID: {} - Motivo: {}", id, motivo);

        Pagamento pagamento = pagamentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado"));

        pagamento.recusar(motivo);
        Pagamento pagamentoSalvo = pagamentoRepository.save(pagamento);

        log.info("Pagamento recusado - ID: {}", pagamentoSalvo.getId());
        return converterParaResponse(pagamentoSalvo);
    }

    /**
     * Cancela um pagamento
     */
    @Transactional
    public PagamentoResponse cancelar(Long id, String motivo) {
        log.info("Cancelando pagamento ID: {} - Motivo: {}", id, motivo);

        Pagamento pagamento = pagamentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado"));

        pagamento.cancelar(motivo);
        Pagamento pagamentoSalvo = pagamentoRepository.save(pagamento);

        log.info("Pagamento cancelado - ID: {}", pagamentoSalvo.getId());
        return converterParaResponse(pagamentoSalvo);
    }

    /**
     * Processa callback do gateway de pagamento
     */
    @Transactional
    public void processarWebhook(String transactionId, String status, String gatewayResponse) {
        log.info("Processando webhook - Transaction ID: {}, Status: {}", transactionId, status);

        Pagamento pagamento = pagamentoRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado com Transaction ID: " + transactionId));

        // Atualiza conforme resposta do gateway
        // TODO: Implementar lógica completa de integração
    }

    /**
     * Converte entity para DTO response
     */
    private PagamentoResponse converterParaResponse(Pagamento pagamento) {
        return PagamentoResponse.builder()
                .id(pagamento.getId())
                .unidadeConsumoId(pagamento.getUnidadeConsumo().getId())
                .referenciaUnidadeConsumo(pagamento.getUnidadeConsumo().getReferencia())
                .valor(pagamento.getValor())
                .metodoPagamento(pagamento.getMetodoPagamento())
                .status(pagamento.getStatus())
                .transactionId(pagamento.getTransactionId())
                .paymentUrl(pagamento.getPaymentUrl())
                .qrCodePix(pagamento.getQrCodePix())
                .processadoEm(pagamento.getProcessadoEm())
                .observacoes(pagamento.getObservacoes())
                .build();
    }
}
