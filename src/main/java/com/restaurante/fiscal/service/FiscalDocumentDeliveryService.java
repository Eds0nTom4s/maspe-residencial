package com.restaurante.fiscal.service;

import com.restaurante.dto.response.FiscalDocumentDeliveryResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.enums.FiscalDocumentStatus;
import com.restaurante.notificacao.service.NotificacaoService;
import com.restaurante.util.PhoneNumberUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class FiscalDocumentDeliveryService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int LINK_VALID_DAYS = 7;

    private final FiscalDocumentRepository documentRepository;
    private final FiscalDocumentService documentService;
    private final NotificacaoService notificacaoService;

    @Value("${consuma.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Transactional
    public FiscalDocumentDeliveryResponse sendBySms(Long documentId, String rawPhone) {
        FiscalDocument document = documentService.getForTenant(documentId);
        if (document == null) throw new BusinessException("Documento fiscal não encontrado.");
        if (document.getStatus() == FiscalDocumentStatus.CANCELLED) {
            throw new BusinessException("Documento cancelado não pode ser enviado.");
        }

        String phone;
        try {
            phone = PhoneNumberUtil.normalize(rawPhone);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(LINK_VALID_DAYS);
        document.setPublicShareTokenHash(hash(token));
        document.setPublicShareExpiresAt(expiresAt);
        documentRepository.save(document);

        String link = normalizedBaseUrl() + "/api/public/fiscal/documents/" + token + "/pdf";
        String label = document.getStatus().name().equals("DRAFT") ? "cotação" : "fatura/recibo";
        boolean sent = notificacaoService.enviarSms(
                phone,
                "CONSUMA: consulte a sua " + label + " em " + link + " (link válido por " + LINK_VALID_DAYS + " dias).",
                "FISCAL_DOCUMENT_LINK"
        );
        if (!sent) throw new BusinessException("Não foi possível enviar o documento por SMS.");
        return new FiscalDocumentDeliveryResponse(document.getId(), mask(phone), expiresAt);
    }

    @Transactional(readOnly = true)
    public FiscalDocument resolvePublicToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new BusinessException("Link de documento inválido.");
        FiscalDocument document = documentRepository.findByPublicShareTokenHash(hash(rawToken.trim()))
                .orElseThrow(() -> new BusinessException("Link de documento inválido."));
        if (document.getPublicShareExpiresAt() == null || !document.getPublicShareExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("Link de documento expirado.");
        }
        if (document.getStatus() == FiscalDocumentStatus.CANCELLED) {
            throw new BusinessException("Documento cancelado não está disponível.");
        }
        return document;
    }

    private String normalizedBaseUrl() {
        String base = publicBaseUrl != null ? publicBaseUrl.trim() : "";
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 indisponível.", ex);
        }
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return "***" + phone.substring(phone.length() - 4);
    }
}
