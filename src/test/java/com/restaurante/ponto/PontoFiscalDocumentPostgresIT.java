package com.restaurante.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.fiscal.repository.FiscalDocumentLineRepository;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.fiscal.repository.ProductTaxClassificationRepository;
import com.restaurante.fiscal.repository.TaxRateRepository;
import com.restaurante.fiscal.repository.TenantFiscalCorrectionPolicyRepository;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.fiscal.repository.TenantTaxPolicyRepository;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.FiscalDocumentLine;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantFiscalProfile;
import com.restaurante.model.enums.FiscalDocumentSource;
import com.restaurante.model.enums.FiscalDocumentStatus;
import com.restaurante.model.enums.FiscalDocumentType;
import com.restaurante.model.enums.FiscalRegime;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantFiscalCorrectionPolicyStatus;
import com.restaurante.model.enums.TenantFiscalProfileStatus;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.testsupport.UniqueTestData;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class PontoFiscalDocumentPostgresIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired TenantProvisioningService provisioning;
    @Autowired TenantRepository tenants;
    @Autowired TenantFiscalProfileRepository profiles;
    @Autowired TenantTaxPolicyRepository taxPolicies;
    @Autowired ProductTaxClassificationRepository productTaxClassifications;
    @Autowired TaxRateRepository taxRates;
    @Autowired TenantFiscalCorrectionPolicyRepository correctionPolicies;
    @Autowired FiscalDocumentRepository documents;
    @Autowired FiscalDocumentLineRepository lines;

    @AfterEach
    void clearContext() { TenantContextHolder.clear(); }

    @Test
    @WithMockUser(username = "ponto-fiscal-owner")
    void generatesTenantOwnedInternalPdfAndServesOnlyHashedUnexpiredPublicToken() throws Exception {
        ProvisionarTenantResponse provisioned = provision();
        Tenant tenant = tenants.findById(provisioned.getTenantId()).orElseThrow();
        TenantContextHolder.set(new TenantContext(tenant.getId(), tenant.getTenantCode(),
                provisioned.getOwnerUserId(), Set.of(TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false));
        TenantFiscalProfile profile = new TenantFiscalProfile();
        profile.setTenant(tenant);
        profile.setStatus(TenantFiscalProfileStatus.ACTIVE);
        profile.setFiscalRegime(FiscalRegime.GENERAL_VAT);
        profile.setTaxpayerNumber("5000000000");
        profile.setLegalName("Ponto Fiscal Controlado, Lda.");
        profile.setFiscalDocumentEnabled(true);
        profiles.saveAndFlush(profile);

        LocalDateTime effectiveAt = LocalDateTime.now();
        assertThat(taxPolicies.findActiveEffective(tenant.getId(), effectiveAt)).isEmpty();
        assertThat(productTaxClassifications
                .findActiveEffectiveByTenantAndProduct(tenant.getId(), Long.MAX_VALUE, effectiveAt)).isEmpty();
        assertThat(taxRates.findEffectiveById(Long.MAX_VALUE, effectiveAt)).isEmpty();
        assertThat(correctionPolicies.findActiveEffective(tenant.getId(),
                TenantFiscalCorrectionPolicyStatus.ACTIVE, effectiveAt)).isEmpty();

        String rawToken = "safe-public-document-token-20260819";
        FiscalDocument document = new FiscalDocument();
        document.setTenant(tenant);
        document.setDocumentType(FiscalDocumentType.INTERNAL_INVOICE_RECEIPT);
        document.setStatus(FiscalDocumentStatus.ISSUED);
        document.setFiscalRegime(FiscalRegime.GENERAL_VAT);
        document.setDocumentNumber("INT-RUNTIME-001");
        document.setSeries("INT");
        document.setIssuedAt(LocalDateTime.now());
        document.setSubtotalAmount(new BigDecimal("500.00"));
        document.setTaxableAmount(new BigDecimal("500.00"));
        document.setExemptAmount(BigDecimal.ZERO);
        document.setTaxAmount(BigDecimal.ZERO);
        document.setTotalAmount(new BigDecimal("500.00"));
        document.setSource(FiscalDocumentSource.ADMIN);
        document.setPublicShareTokenHash(hash(rawToken));
        document.setPublicShareExpiresAt(LocalDateTime.now().plusDays(1));
        document = documents.saveAndFlush(document);
        FiscalDocumentLine line = new FiscalDocumentLine();
        line.setTenant(tenant);
        line.setFiscalDocument(document);
        line.setDescription("Venda controlada PONTO");
        line.setQuantity(1);
        line.setUnitPrice(new BigDecimal("500.00"));
        line.setNetAmount(new BigDecimal("500.00"));
        line.setTaxAmount(BigDecimal.ZERO);
        line.setGrossAmount(new BigDecimal("500.00"));
        lines.saveAndFlush(line);

        byte[] tenantPdf = mockMvc.perform(get("/tenant/fiscal/documents/{id}/pdf", document.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(tenantPdf).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));

        TenantContextHolder.clear();
        byte[] publicPdf = mockMvc.perform(get("/public/fiscal/documents/{token}/pdf", rawToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(publicPdf).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        mockMvc.perform(get("/public/fiscal/documents/{token}/pdf", "unknown-token"))
                .andExpect(status().isBadRequest());

        TenantContextHolder.set(new TenantContext(tenant.getId(), tenant.getTenantCode(),
                provisioned.getOwnerUserId(), Set.of(TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false));
        mockMvc.perform(post("/tenant/fiscal/documents/{id}/send-sms", document.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+244923000000\"}"))
                .andExpect(status().isBadRequest());
    }

    private ProvisionarTenantResponse provision() {
        TenantContextHolder.set(new TenantContext(null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false));
        return provisioning.provisionar(ProvisionarTenantRequest.builder()
                .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                        .nome("Ponto Fiscal").slug(UniqueTestData.uniqueSlug("ponto-fiscal"))
                        .tenantCode(UniqueTestData.uniqueTenantCode("PFS")).tipo(TenantTipo.LOJA).build())
                .planoCodigo("PILOTO").templateCodigo("VENDEDOR_RUA")
                .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                        .nome("Instituição Fiscal")
                        .sigla(UniqueTestData.uniqueInstituicaoSigla("PFS")).build())
                .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                        .email(UniqueTestData.uniqueEmail("ponto-fiscal"))
                        .telefone(UniqueTestData.uniqueTelefone()).criarUsuario(true).build())
                .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder()
                        .criarMesas(false).criarQrPorMesa(false).criarQrPrincipal(false).build())
                .build());
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
