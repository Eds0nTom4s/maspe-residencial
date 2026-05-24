package com.restaurante.fiscal.official;

import com.restaurante.fiscal.official.service.OfficialFiscalDocumentMapper;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.FiscalDocumentLine;
import com.restaurante.model.entity.TenantOfficialFiscalProfile;
import com.restaurante.model.enums.FiscalAuthority;
import com.restaurante.model.enums.FiscalDocumentType;
import com.restaurante.model.enums.FiscalRegime;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OfficialFiscalDocumentMapperTest {

    @Test
    void mapeiaFiscalDocument_paraPayloadAbstrato() {
        OfficialFiscalDocumentMapper mapper = new OfficialFiscalDocumentMapper();

        TenantOfficialFiscalProfile profile = new TenantOfficialFiscalProfile();
        profile.setAuthority(FiscalAuthority.AGT_AO);
        profile.setCountryCode("AO");
        profile.setTaxpayerNumber("5000000000");
        profile.setSoftwareCertificateId("CERT-TEST");

        FiscalDocument d = new FiscalDocument();
        d.setDocumentType(FiscalDocumentType.INTERNAL_RECEIPT);
        d.setDocumentNumber("INT-2026-000001");
        d.setSeries("A");
        d.setIssuedAt(LocalDateTime.now());
        d.setFiscalRegime(FiscalRegime.GENERAL_VAT);
        d.setCurrency("AOA");
        d.setTaxableAmount(new BigDecimal("100.00"));
        d.setExemptAmount(BigDecimal.ZERO);
        d.setTaxAmount(new BigDecimal("14.00"));
        d.setTotalAmount(new BigDecimal("114.00"));

        FiscalDocumentLine l = new FiscalDocumentLine();
        l.setDescription("Item");
        l.setQuantity(1);
        l.setUnitPrice(new BigDecimal("114.00"));
        l.setNetAmount(new BigDecimal("100.00"));
        l.setTaxRateCode("AO_VAT_STANDARD_14");
        l.setTaxRateValue(new BigDecimal("14.00"));
        l.setTaxAmount(new BigDecimal("14.00"));
        l.setGrossAmount(new BigDecimal("114.00"));
        l.setTaxCategory(com.restaurante.model.enums.TaxCategory.STANDARD);

        var payload = mapper.mapFromFiscalDocument(d, List.of(l), profile);
        assertThat(payload.getAuthority()).isEqualTo("AGT_AO");
        assertThat(payload.getCountryCode()).isEqualTo("AO");
        assertThat(payload.getTaxpayerNumber()).isEqualTo("5000000000");
        assertThat(payload.getSoftwareCertificateId()).isEqualTo("CERT-TEST");
        assertThat(payload.getDocumentNumber()).isEqualTo("INT-2026-000001");
        assertThat(payload.getTotals().getTotalAmount()).isEqualByComparingTo("114.00");
        assertThat(payload.getLines()).hasSize(1);
    }
}

