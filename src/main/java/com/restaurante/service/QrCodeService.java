package com.restaurante.service;

import com.google.zxing.WriterException;
import com.restaurante.dto.request.GerarQrCodeRequest;
import com.restaurante.dto.response.QrCodeResponse;
import com.restaurante.dto.response.QrCodeValidacaoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.QrCodeToken;
import com.restaurante.model.entity.UnidadeDeConsumo;
import com.restaurante.model.enums.StatusQrCode;
import com.restaurante.model.enums.TipoQrCode;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.QrCodeTokenRepository;
import com.restaurante.repository.UnidadeDeConsumoRepository;
import com.restaurante.util.QrCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service para gerenciamento de QR Codes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QrCodeService {

    private final QrCodeTokenRepository qrCodeTokenRepository;
    private final UnidadeDeConsumoRepository unidadeDeConsumoRepository;
    private final PedidoRepository pedidoRepository;
    private final QrCodeGenerator qrCodeGenerator;

    @Value("${app.base-url:http://localhost:8080/api}")
    private String baseUrl;

    /**
     * Gera novo QR Code
     */
    @Transactional
    public QrCodeResponse gerarQrCode(GerarQrCodeRequest request) {
        log.info("Gerando QR Code tipo: {}", request.getTipo());

        // Validações por tipo
        validarRequest(request);

        // Busca entidades relacionadas
        UnidadeDeConsumo unidadeDeConsumo = null;
        Pedido pedido = null;

        if (request.getUnidadeDeConsumoId() != null) {
            unidadeDeConsumo = unidadeDeConsumoRepository.findById(request.getUnidadeDeConsumoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unidade de Consumo não encontrada"));
        }

        if (request.getPedidoId() != null) {
            pedido = pedidoRepository.findById(request.getPedidoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
        }

        // Para QR Code de mesa, verifica se já existe um ativo
        if (request.getTipo() == TipoQrCode.MESA && unidadeDeConsumo != null) {
            Optional<QrCodeToken> qrExistente = qrCodeTokenRepository
                    .findByUnidadeDeConsumoIdAndTipoAndStatus(
                            unidadeDeConsumo.getId(), 
                            TipoQrCode.MESA, 
                            StatusQrCode.ATIVO
                    );
            
            if (qrExistente.isPresent() && !qrExistente.get().isExpirado()) {
                log.info("QR Code ativo já existe para mesa {}, renovando", unidadeDeConsumo.getId());
                QrCodeToken qr = qrExistente.get();
                qr.renovar();
                qr = qrCodeTokenRepository.save(qr);
                return toResponse(qr);
            }
        }

        // Calcula validade
        long validadeMinutos = request.getValidadeMinutos() != null 
                ? request.getValidadeMinutos() 
                : request.getTipo().getValidadeMinutos();

        LocalDateTime expiraEm = LocalDateTime.now().plusMinutes(validadeMinutos);

        // Cria novo QR Code
        QrCodeToken qrCodeToken = QrCodeToken.builder()
                .tipo(request.getTipo())
                .status(StatusQrCode.ATIVO)
                .expiraEm(expiraEm)
                .unidadeDeConsumo(unidadeDeConsumo)
                .pedido(pedido)
                .metadados(request.getMetadados())
                .build();

        qrCodeToken = qrCodeTokenRepository.save(qrCodeToken);

        log.info("QR Code gerado com sucesso: {} (expira em: {})", qrCodeToken.getToken(), expiraEm);

        return toResponse(qrCodeToken);
    }

    /**
     * Valida token de QR Code
     */
    @Transactional
    public QrCodeValidacaoResponse validarQrCode(String token) {
        log.info("Validando QR Code: {}", token);

        Optional<QrCodeToken> qrOpt = qrCodeTokenRepository.findByToken(token);

        if (qrOpt.isEmpty()) {
            return QrCodeValidacaoResponse.builder()
                    .valido(false)
                    .mensagem("QR Code não encontrado")
                    .motivoInvalido("NAO_ENCONTRADO")
                    .build();
        }

        QrCodeToken qr = qrOpt.get();

        // Verifica se expirou
        if (qr.isExpirado()) {
            qr.marcarComoExpirado();
            qrCodeTokenRepository.save(qr);
            
            return QrCodeValidacaoResponse.builder()
                    .valido(false)
                    .mensagem("QR Code expirado")
                    .motivoInvalido("EXPIRADO")
                    .qrCode(toResponse(qr))
                    .build();
        }

        // Verifica status
        if (!qr.getStatus().isUsavel()) {
            return QrCodeValidacaoResponse.builder()
                    .valido(false)
                    .mensagem("QR Code " + qr.getStatus().getDescricao())
                    .motivoInvalido(qr.getStatus().name())
                    .qrCode(toResponse(qr))
                    .build();
        }

        log.info("QR Code válido: {}", token);

        return QrCodeValidacaoResponse.builder()
                .valido(true)
                .mensagem("QR Code válido")
                .qrCode(toResponse(qr))
                .build();
    }

    /**
     * Marca QR Code como usado (para uso único)
     */
    @Transactional
    public QrCodeResponse usarQrCode(String token) {
        QrCodeToken qr = qrCodeTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("QR Code não encontrado"));

        if (!qr.isValido()) {
            throw new BusinessException("QR Code não está válido para uso");
        }

        if (!qr.getTipo().isUsoMultiplo()) {
            String usuario = getUsuarioAtual();
            qr.marcarComoUsado(usuario);
            qr = qrCodeTokenRepository.save(qr);
            log.info("QR Code marcado como usado: {} por {}", token, usuario);
        }

        return toResponse(qr);
    }

    /**
     * Renova QR Code (apenas tipo MESA)
     */
    @Transactional
    public QrCodeResponse renovarQrCode(String token) {
        QrCodeToken qr = qrCodeTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("QR Code não encontrado"));

        if (!qr.getTipo().isUsoMultiplo()) {
            throw new BusinessException("Apenas QR Codes de mesa podem ser renovados");
        }

        qr.renovar();
        qr = qrCodeTokenRepository.save(qr);

        log.info("QR Code renovado: {} (nova expiração: {})", token, qr.getExpiraEm());

        return toResponse(qr);
    }

    /**
     * Cancela QR Code
     */
    @Transactional
    public void cancelarQrCode(String token) {
        QrCodeToken qr = qrCodeTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("QR Code não encontrado"));

        qr.cancelar();
        qrCodeTokenRepository.save(qr);

        log.info("QR Code cancelado: {}", token);
    }

    /**
     * Busca QR Codes por Unidade de Consumo
     */
    @Transactional(readOnly = true)
    public List<QrCodeResponse> buscarPorUnidadeDeConsumo(Long unidadeDeConsumoId) {
        List<QrCodeToken> qrCodes = qrCodeTokenRepository
                .findByUnidadeDeConsumoIdAndStatus(unidadeDeConsumoId, StatusQrCode.ATIVO);
        
        return qrCodes.stream().map(this::toResponse).toList();
    }

    /**
     * Gera imagem PNG do QR Code
     */
    public byte[] gerarImagemQrCode(String token) throws WriterException, IOException {
        QrCodeToken qr = qrCodeTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("QR Code não encontrado"));

        String url = qr.getUrl(baseUrl);
        return qrCodeGenerator.generateQrCodeImage(url);
    }

    /**
     * Gera imagem PNG do QR Code para impressão (alta resolução)
     */
    public byte[] gerarImagemQrCodeParaImpressao(String token) throws WriterException, IOException {
        QrCodeToken qr = qrCodeTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("QR Code não encontrado"));

        String url = qr.getUrl(baseUrl);
        return qrCodeGenerator.generateQrCodePrint(url);
    }

    /**
     * Job agendado para expirar QR Codes automaticamente
     * Executa a cada 1 hora
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expirarQrCodesAutomaticamente() {
        log.info("Executando job de expiração de QR Codes");
        
        List<QrCodeToken> expirados = qrCodeTokenRepository.findExpirados(LocalDateTime.now());
        
        for (QrCodeToken qr : expirados) {
            qr.marcarComoExpirado();
        }
        
        if (!expirados.isEmpty()) {
            qrCodeTokenRepository.saveAll(expirados);
            log.info("{} QR Codes expirados automaticamente", expirados.size());
        }
    }

    /**
     * Job agendado para renovar QR Codes de mesa automaticamente
     * Executa diariamente às 6h da manhã
     */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void renovarQrCodesDeMesaAutomaticamente() {
        log.info("Executando job de renovação automática de QR Codes de mesa");
        
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime limite = agora.plusHours(2); // Renova se expira nas próximas 2h
        
        List<QrCodeToken> expirandoEmBreve = qrCodeTokenRepository
                .findExpirandoEmBreve(agora, limite);
        
        for (QrCodeToken qr : expirandoEmBreve) {
            qr.renovar();
        }
        
        if (!expirandoEmBreve.isEmpty()) {
            qrCodeTokenRepository.saveAll(expirandoEmBreve);
            log.info("{} QR Codes de mesa renovados automaticamente", expirandoEmBreve.size());
        }
    }

    /**
     * Limpa QR Codes antigos (expirados há mais de 30 dias)
     * Executa mensalmente
     */
    @Scheduled(cron = "0 0 3 1 * *")
    @Transactional
    public void limparQrCodesAntigos() {
        log.info("Executando limpeza de QR Codes antigos");
        
        LocalDateTime dataLimite = LocalDateTime.now().minusDays(30);
        qrCodeTokenRepository.deleteExpiradosAntigos(dataLimite);
        
        log.info("QR Codes antigos removidos (expirados antes de {})", dataLimite);
    }

    // ========== Métodos auxiliares ==========

    private void validarRequest(GerarQrCodeRequest request) {
        if (request.getTipo() == TipoQrCode.MESA && request.getUnidadeDeConsumoId() == null) {
            throw new BusinessException("UnidadeDeConsumoId é obrigatório para QR Code de mesa");
        }

        if ((request.getTipo() == TipoQrCode.ENTREGA || request.getTipo() == TipoQrCode.PAGAMENTO) 
                && request.getPedidoId() == null) {
            throw new BusinessException("PedidoId é obrigatório para QR Code de entrega/pagamento");
        }
    }

    private String getUsuarioAtual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    private QrCodeResponse toResponse(QrCodeToken qr) {
        return QrCodeResponse.builder()
                .id(qr.getId())
                .token(qr.getToken())
                .tipo(qr.getTipo())
                .status(qr.getStatus())
                .expiraEm(qr.getExpiraEm())
                .unidadeDeConsumoId(qr.getUnidadeDeConsumo() != null ? qr.getUnidadeDeConsumo().getId() : null)
                .unidadeDeConsumoNome(qr.getUnidadeDeConsumo() != null ? qr.getUnidadeDeConsumo().getIdentificador() : null)
                .pedidoId(qr.getPedido() != null ? qr.getPedido().getId() : null)
                .usadoEm(qr.getUsadoEm())
                .usadoPor(qr.getUsadoPor())
                .metadados(qr.getMetadados())
                .url(qr.getUrl(baseUrl))
                .valido(qr.isValido())
                .expirado(qr.isExpirado())
                .criadoEm(qr.getCreatedAt())
                .criadoPor(qr.getCreatedBy())
                .build();
    }
}
