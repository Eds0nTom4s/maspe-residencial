package com.restaurante.fiscal;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiscalDocumentPdfServiceTest {

    @Mock FiscalDocumentLineRepository lineRepository;
    @Mock TenantFiscalProfileRepository profileRepository;

    @Test
    void rendersQuoteAsRealPdfWithIssuerAndLines() {
        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setNome("Restaurante Luanda");
        tenant.setNif("5000000000");

        TenantFiscalProfile profile = new TenantFiscalProfile();
        profile.setTenant(tenant);
        profile.setLegalName("Restaurante Luanda, Lda.");
        profile.setTaxpayerNumber("5000000000");
        profile.setAddress("Rua Principal");

        FiscalDocument document = new FiscalDocument();
        document.setId(20L);
        document.setTenant(tenant);
        document.setStatus(FiscalDocumentStatus.DRAFT);
        document.setDocumentType(FiscalDocumentType.INTERNAL_INVOICE);
        document.setFiscalRegime(FiscalRegime.GENERAL_VAT);
        document.setDocumentNumber("INT-2026-000001");
        document.setSeries("Q");
        document.setIssuedAt(LocalDateTime.of(2026, 8, 4, 10, 0));
        document.setSubtotalAmount(new BigDecimal("1000.00"));
        document.setTaxAmount(new BigDecimal("140.00"));
        document.setTotalAmount(new BigDecimal("1140.00"));
        document.setSource(FiscalDocumentSource.ADMIN);

        FiscalDocumentLine line = new FiscalDocumentLine();
        line.setDescription("Serviço de catering");
        line.setQuantity(1);
        line.setUnitPrice(new BigDecimal("1000.00"));
        line.setGrossAmount(new BigDecimal("1140.00"));

        when(profileRepository.findByTenantId(10L)).thenReturn(Optional.of(profile));
        when(lineRepository.findByFiscalDocumentIdOrderByIdAsc(20L)).thenReturn(List.of(line));

        var file = new FiscalDocumentPdfService(lineRepository, profileRepository).render(document);

        assertThat(file.filename()).startsWith("cotacao-").endsWith(".pdf");
        assertThat(file.bytes()).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(file.bytes().length).isGreaterThan(500);
    }
}
