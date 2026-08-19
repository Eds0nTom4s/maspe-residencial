package com.restaurante.fiscal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.restaurante.fiscal.repository.FiscalDocumentLineRepository;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.fiscal.service.FiscalDocumentPdfService;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.FiscalDocumentLine;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantFiscalProfile;
import com.restaurante.model.enums.FiscalDocumentSource;
import com.restaurante.model.enums.FiscalDocumentStatus;
import com.restaurante.model.enums.FiscalDocumentType;
import com.restaurante.model.enums.FiscalRegime;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FiscalDocumentPdfServiceTest {

    @Mock FiscalDocumentLineRepository lines;
    @Mock TenantFiscalProfileRepository profiles;

    @Test
    void rendersInternalPlatformDocumentWithExplicitNonAgtDisclaimer() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setNome("Restaurante Luanda");
        tenant.setNif("5000000000");
        TenantFiscalProfile profile = new TenantFiscalProfile();
        profile.setTenant(tenant);
        profile.setLegalName("Restaurante Luanda, Lda.");
        profile.setTaxpayerNumber("5000000000");
        FiscalDocument document = new FiscalDocument();
        document.setId(20L);
        document.setTenant(tenant);
        document.setStatus(FiscalDocumentStatus.ISSUED);
        document.setDocumentType(FiscalDocumentType.INTERNAL_INVOICE_RECEIPT);
        document.setFiscalRegime(FiscalRegime.GENERAL_VAT);
        document.setDocumentNumber("INT-2026-000001");
        document.setSeries("I");
        document.setIssuedAt(LocalDateTime.of(2026, 8, 19, 10, 0));
        document.setSubtotalAmount(new BigDecimal("1000.00"));
        document.setTaxAmount(new BigDecimal("140.00"));
        document.setTotalAmount(new BigDecimal("1140.00"));
        document.setSource(FiscalDocumentSource.ADMIN);
        FiscalDocumentLine line = new FiscalDocumentLine();
        line.setDescription("Serviço de catering");
        line.setQuantity(1);
        line.setUnitPrice(new BigDecimal("1000.00"));
        line.setGrossAmount(new BigDecimal("1140.00"));
        when(profiles.findByTenantId(10L)).thenReturn(Optional.of(profile));
        when(lines.findByFiscalDocumentIdOrderByIdAsc(20L)).thenReturn(List.of(line));

        var file = new FiscalDocumentPdfService(lines, profiles).render(document);

        assertThat(file.filename()).isEqualTo("documento-interno-INT-2026-000001.pdf");
        assertThat(file.bytes()).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        try (var pdf = Loader.loadPDF(file.bytes())) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("DOCUMENTO INTERNO DA PLATAFORMA")
                    .contains("NÃO REPRESENTA CERTIFICAÇÃO OU SUBMISSÃO ELECTRÓNICA OFICIAL À AGT")
                    .contains("Restaurante Luanda, Lda.")
                    .contains("TOTAL:")
                    .contains("1\u00a0140,00");
        }
    }
}
