package com.restaurante.service;

import com.google.zxing.WriterException;
import com.restaurante.dto.request.GerarQrCodeRequest;
import com.restaurante.dto.response.QrCodeResponse;
import com.restaurante.dto.response.QrCodeValidacaoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.QrCodeToken;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.enums.StatusQrCode;
import com.restaurante.model.enums.TipoQrCode;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.QrCodeTokenRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.util.QrCodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class QrCodeService {

    private static final Logger log = LoggerFactory.getLogger(QrCodeService.class);

    private final QrCodeTokenRepository qrCodeTokenRepository;
    private final MesaRepository mesaRepository;
    private final PedidoRepository pedidoRepository;
    private final QrCodeGenerator qrCodeGenerator;
    private final InstituicaoRepository instituicaoRepository;

    public QrCodeService(QrCodeTokenRepository qrCodeTokenRepository, MesaRepository mesaRepository, PedidoRepository pedidoRepository, QrCodeGenerator qrCodeGenerator, InstituicaoRepository instituicaoRepository) {
        this.qrCodeTokenRepository = qrCodeTokenRepository;
        this.mesaRepository = mesaRepository;
        this.pedidoRepository = pedidoRepository;
        this.qrCodeGenerator = qrCodeGenerator;
        this.instituicaoRepository = instituicaoRepository;
    }

    @Value("${app.base-url:http://localhost:8080/api}")
    private String baseUrl;

    @Value("${app.client-url:http://localhost:3000}")
    private String clientUrl;

    /**
     * Gera novo QR Code
     */
    @Transactional
    public QrCodeResponse gerarQrCode(GerarQrCodeRequest request) {
        log.info("Gerando QR Code tipo: {}", request.getTipo());

        // Validações por tipo
        validarRequest(request);

        // Busca entidades relacionadas
        Mesa mesa = null;
        Pedido pedido = null;

        if (request.getMesaId() != null) {
            mesa = mesaRepository.findById(request.getMesaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada"));
        }
        Instituicao instituicao = resolverInstituicao(mesa);

        if (request.getPedidoId() != null) {
            pedido = pedidoRepository.findById(request.getPedidoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
        }

        // Para QR Code de mesa, usa código estável e legível para impressão/digitação.
        if (request.getTipo() == TipoQrCode.MESA && mesa != null) {
            String tokenMesa = gerarTokenMesa(mesa, instituicao);
            long validadeMinutos = request.getValidadeMinutos() != null
                    ? request.getValidadeMinutos()
                    : request.getTipo().getValidadeMinutos();
            LocalDateTime expiraEm = LocalDateTime.now().plusMinutes(validadeMinutos);

            Optional<QrCodeToken> qrPorToken = qrCodeTokenRepository.findByToken(tokenMesa);
            if (qrPorToken.isPresent()) {
                QrCodeToken qr = qrPorToken.get();
                if (qr.getMesa() == null || !qr.getMesa().getId().equals(mesa.getId())) {
                    throw new BusinessException("Código de mesa já está associado a outra mesa: " + tokenMesa);
                }
                qr.setStatus(StatusQrCode.ATIVO);
                qr.setExpiraEm(expiraEm);
                qr.setMetadados(request.getMetadados());
                qr.setInstituicao(instituicao);
                mesa.setQrCode(tokenMesa);
                mesa.setInstituicao(instituicao);
                mesaRepository.save(mesa);
                return toResponse(qrCodeTokenRepository.save(qr));
            }

            Optional<QrCodeToken> qrExistente = qrCodeTokenRepository
                    .findByMesaIdAndTipoAndStatus(
                            mesa.getId(),
                            TipoQrCode.MESA,
                            StatusQrCode.ATIVO
                    );
            
            if (qrExistente.isPresent() && !qrExistente.get().isExpirado()) {
                log.info("QR Code ativo já existe para mesa {}, normalizando token", mesa.getId());
                QrCodeToken qr = qrExistente.get();
                qr.setToken(tokenMesa);
                qr.setExpiraEm(expiraEm);
                qr.setStatus(StatusQrCode.ATIVO);
                qr.setMetadados(request.getMetadados());
                qr.setInstituicao(instituicao);
                mesa.setQrCode(tokenMesa);
                mesa.setInstituicao(instituicao);
                mesaRepository.save(mesa);
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
                .token(request.getTipo() == TipoQrCode.MESA && mesa != null ? gerarTokenMesa(mesa, instituicao) : null)
                .tipo(request.getTipo())
                .status(StatusQrCode.ATIVO)
                .expiraEm(expiraEm)
                .mesa(mesa)
                .pedido(pedido)
                .instituicao(instituicao)
                .metadados(request.getMetadados())
                .build();

        qrCodeToken = qrCodeTokenRepository.save(qrCodeToken);

        if (request.getTipo() == TipoQrCode.MESA && mesa != null) {
            mesa.setQrCode(qrCodeToken.getToken());
            mesa.setInstituicao(instituicao);
            mesaRepository.save(mesa);
        }

        log.info("QR Code gerado com sucesso: {} (expira em: {})", qrCodeToken.getToken(), expiraEm);

        return toResponse(qrCodeToken);
    }

    /**
     * Valida token de QR Code
     */
    @Transactional
    public QrCodeValidacaoResponse validarQrCode(String token) {
        log.info("Validando QR Code: {}", token);

        String tokenNormalizado = normalizarTokenMesa(token);
        Optional<QrCodeToken> qrOpt = qrCodeTokenRepository.findByToken(tokenNormalizado);

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

        log.info("QR Code válido: {}", tokenNormalizado);

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
     * Busca QR Codes por Mesa
     */
    @Transactional(readOnly = true)
    public List<QrCodeResponse> buscarPorMesa(Long mesaId) {
        List<QrCodeToken> qrCodes = qrCodeTokenRepository
                .findByMesaIdAndStatus(mesaId, StatusQrCode.ATIVO);
        
        return qrCodes.stream().map(this::toResponse).toList();
    }

    /**
     * Gera imagem PNG do QR Code
     */
    public byte[] gerarImagemQrCode(String token) throws WriterException, IOException {
        QrCodeToken qr = qrCodeTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("QR Code não encontrado"));

        String url = montarUrlCliente(qr.getToken());
        return qrCodeGenerator.generateQrCodeImage(url);
    }

    /**
     * Gera imagem PNG do QR Code para impressão (alta resolução)
     */
    public byte[] gerarImagemQrCodeParaImpressao(String token) throws WriterException, IOException {
        QrCodeToken qr = qrCodeTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("QR Code não encontrado"));

        String url = montarUrlCliente(qr.getToken());
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
        if (request.getTipo() == TipoQrCode.MESA && request.getMesaId() == null) {
            throw new BusinessException("MesaId é obrigatório para QR Code de mesa");
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
                .mesaId(qr.getMesa() != null ? qr.getMesa().getId() : null)
                .referenciaMesa(qr.getMesa() != null ? qr.getMesa().getIdentificador() : null)
                .pedidoId(qr.getPedido() != null ? qr.getPedido().getId() : null)
                .usadoEm(qr.getUsadoEm())
                .usadoPor(qr.getUsadoPor())
                .metadados(qr.getMetadados())
                .url(qr.getTipo() == TipoQrCode.MESA ? montarUrlCliente(qr.getToken()) : qr.getUrl(baseUrl))
                .valido(qr.isValido())
                .expirado(qr.isExpirado())
                .criadoEm(qr.getCreatedAt())
                .criadoPor(qr.getCreatedBy())
                .build();
    }

    private String gerarTokenMesa(Mesa mesa, Instituicao instituicao) {
        return String.format("%s-MESA-%04d", instituicao.getSigla(), mesa.getId());
    }

    private String montarUrlCliente(String token) {
        String normalizedClientUrl = clientUrl != null && clientUrl.endsWith("/")
                ? clientUrl.substring(0, clientUrl.length() - 1)
                : clientUrl;
        return normalizedClientUrl + "/?qr=" + token;
    }

    private String normalizarTokenMesa(String token) {
        String normalized = token != null ? token.trim().toUpperCase() : "";
        if (normalized.startsWith("MESA-")) {
            Instituicao instituicao = resolverInstituicao(null);
            return instituicao.getSigla() + "-" + normalized;
        }
        return normalized;
    }

    private Instituicao resolverInstituicao(Mesa mesa) {
        if (mesa != null && mesa.getInstituicao() != null) {
            return mesa.getInstituicao();
        }
        if (mesa != null && mesa.getUnidadeAtendimento() != null && mesa.getUnidadeAtendimento().getInstituicao() != null) {
            return mesa.getUnidadeAtendimento().getInstituicao();
        }
        return instituicaoRepository.findFirstByAtivaTrue()
                .orElseThrow(() -> new BusinessException("Nenhuma instituição ativa configurada"));
    }
}
