package com.restaurante.fiscal;

import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.fiscal.service.FiscalDocumentDeliveryService;
import com.restaurante.fiscal.service.FiscalDocumentService;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.enums.FiscalDocumentStatus;
import com.restaurante.notificacao.service.NotificacaoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiscalDocumentDeliveryServiceTest {

    @Mock FiscalDocumentRepository documentRepository;
    @Mock FiscalDocumentService documentService;
    @Mock NotificacaoService notificacaoService;

    @Test
    void sendsExpiringPdfLinkAndPersistsOnlyTokenHash() {
        FiscalDocument document = new FiscalDocument();
        document.setId(20L);
        document.setStatus(FiscalDocumentStatus.ISSUED);

        when(documentService.getForTenant(20L)).thenReturn(document);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        when(notificacaoService.enviarSms(eq("+244923000000"), messageCaptor.capture(), eq("FISCAL_DOCUMENT_LINK")))
                .thenReturn(true);

        FiscalDocumentDeliveryService service = new FiscalDocumentDeliveryService(
                documentRepository,
                documentService,
                notificacaoService
        );
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://consuma.example/");

        LocalDateTime before = LocalDateTime.now().plusDays(6);
        var result = service.sendBySms(20L, "923 000 000");

        assertThat(result.documentId()).isEqualTo(20L);
        assertThat(result.maskedPhone()).isEqualTo("***0000");
        assertThat(result.linkExpiresAt()).isAfter(before);
        assertThat(document.getPublicShareExpiresAt()).isEqualTo(result.linkExpiresAt());
        assertThat(document.getPublicShareTokenHash()).matches("[0-9a-f]{64}");

        String message = messageCaptor.getValue();
        assertThat(message).contains("https://consuma.example/api/public/fiscal/documents/").contains("/pdf");
        String token = message.substring(
                message.indexOf("/documents/") + "/documents/".length(),
                message.indexOf("/pdf")
        );
        assertThat(token).hasSizeGreaterThan(40);
        assertThat(document.getPublicShareTokenHash()).doesNotContain(token);
        verify(documentRepository).save(document);
    }

    @Test
    void refusesToSendCancelledDocument() {
        FiscalDocument document = new FiscalDocument();
        document.setId(21L);
        document.setStatus(FiscalDocumentStatus.CANCELLED);
        when(documentService.getForTenant(21L)).thenReturn(document);

        FiscalDocumentDeliveryService service = new FiscalDocumentDeliveryService(
                documentRepository,
                documentService,
                notificacaoService
        );

        assertThatThrownBy(() -> service.sendBySms(21L, "923000000"))
                .hasMessage("Documento cancelado não pode ser enviado.");
    }
}
