package com.restaurante.fiscal.service;

import com.restaurante.dto.response.FiscalDocumentDeliveryResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.enums.FiscalDocumentStatus;
import com.restaurante.notificacao.service.NotificacaoService;
import com.restaurante.util.PhoneNumberUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FiscalDocumentDeliveryService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int LINK_VALID_DAYS = 7;

    private final FiscalDocumentRepository documentRepository;
    private final FiscalDocumentService documentService;
    private final NotificacaoService notificacaoService;
    private final Clock clock;

    @Value("${consuma.fiscal-document.sms-delivery-enabled:false}")
    private boolean smsDeliveryEnabled;

    @Value("${consuma.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Transactional
    public FiscalDocumentDeliveryResponse sendBySms(Long documentId, String rawPhone) {
        if (!smsDeliveryEnabled) {
            throw new BusinessException("Entrega externa de documento por SMS não está certificada neste ambiente.");
        }
        FiscalDocument document = documentService.getForTenant(documentId);
        assertShareable(document);
        String phone;
        try {
            phone = PhoneNumberUtil.normalize(rawPhone);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusDays(LINK_VALID_DAYS);
        document.setPublicShareTokenHash(hash(token));
        document.setPublicShareExpiresAt(expiresAt);
        documentRepository.save(document);

        String link = normalizedBaseUrl() + "/public/fiscal/documents/" + token + "/pdf";
        boolean sent = notificacaoService.enviarSms(phone,
                "CONSUMA: consulte o seu documento interno da plataforma em " + link
                        + " (link válido por " + LINK_VALID_DAYS + " dias).",
                "INTERNAL_DOCUMENT_LINK");
        if (!sent) throw new BusinessException("Não foi possível enviar o documento por SMS.");
        return new FiscalDocumentDeliveryResponse(document.getId(), mask(phone), expiresAt);
    }

    @Transactional(readOnly = true)
    public FiscalDocument resolvePublicToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 100) {
            throw new BusinessException("Link de documento inválido.");
        }
        FiscalDocument document = documentRepository.findByPublicShareTokenHash(hash(rawToken.trim()))
                .orElseThrow(() -> new BusinessException("Link de documento inválido."));
        if (document.getPublicShareExpiresAt() == null
                || !document.getPublicShareExpiresAt().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException("Link de documento expirado.");
        }
        assertShareable(document);
        return document;
    }

    @Transactional
    public void revokeForTenant(Long documentId) {
        FiscalDocument document = documentService.getForTenant(documentId);
        if (document == null) throw new BusinessException("Documento interno não encontrado.");
        document.setPublicShareTokenHash(null);
        document.setPublicShareExpiresAt(null);
        documentRepository.save(document);
    }

    private void assertShareable(FiscalDocument document) {
        if (document == null) throw new BusinessException("Documento interno não encontrado.");
        if (document.getStatus() == FiscalDocumentStatus.CANCELLED) {
            throw new BusinessException("Documento cancelado não está disponível.");
        }
    }

    private String normalizedBaseUrl() {
        String base = publicBaseUrl != null ? publicBaseUrl.trim() : "";
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!base.startsWith("https://") && !base.startsWith("http://localhost")
                && !base.startsWith("http://127.0.0.1")) {
            throw new BusinessException("URL pública HTTPS é obrigatória para entrega documental.");
        }
        return base;
    }

    private static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 indisponível.", ex);
        }
    }

    private static String mask(String phone) {
        return phone == null || phone.length() < 4 ? "****" : "***" + phone.substring(phone.length() - 4);
    }
}
