package com.restaurante.controller;

import com.restaurante.dto.request.CancelFiscalDocumentRequest;
import com.restaurante.dto.request.IssueFiscalDocumentRequest;
import com.restaurante.dto.request.UpsertProductTaxClassificationRequest;
import com.restaurante.dto.request.UpsertTenantFiscalProfileRequest;
import com.restaurante.dto.request.UpsertTenantTaxPolicyRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.FiscalDocumentResponse;
import com.restaurante.dto.response.ProductTaxClassificationResponse;
import com.restaurante.dto.response.TenantFiscalProfileResponse;
import com.restaurante.dto.response.TenantTaxPolicyResponse;
import com.restaurante.fiscal.repository.TaxRateRepository;
import com.restaurante.fiscal.repository.FiscalDocumentLineRepository;
import com.restaurante.fiscal.service.FiscalDocumentService;
import com.restaurante.fiscal.service.ProductTaxClassificationService;
import com.restaurante.fiscal.service.TenantFiscalProfileService;
import com.restaurante.fiscal.service.TenantTaxPolicyService;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.FiscalDocumentLine;
import com.restaurante.model.entity.ProductTaxClassification;
import com.restaurante.model.entity.TaxRate;
import com.restaurante.model.entity.TenantFiscalProfile;
import com.restaurante.model.entity.TenantTaxPolicy;
import com.restaurante.model.enums.FiscalDocumentStatus;
import com.restaurante.model.enums.TaxRateStatus;
import com.restaurante.model.enums.TaxType;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant/fiscal")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Tenant Admin - Fiscal", description = "Fiscalidade mínima por tenant (IVA/documento interno)")
public class TenantFiscalController {

    private final TenantGuard tenantGuard;
    private final TenantFiscalProfileService profileService;
    private final TenantTaxPolicyService policyService;
    private final ProductTaxClassificationService classificationService;
    private final FiscalDocumentService fiscalDocumentService;
    private final TaxRateRepository taxRateRepository;
    private final FiscalDocumentLineRepository fiscalDocumentLineRepository;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<TenantFiscalProfileResponse>> getProfile() {
        TenantFiscalProfile p = profileService.getCurrent();
        return ResponseEntity.ok(ApiResponse.success("Perfil fiscal", p != null ? map(p) : null));
    }

    @PostMapping("/profile")
    public ResponseEntity<ApiResponse<TenantFiscalProfileResponse>> createOrUpdateProfile(@Valid @RequestBody UpsertTenantFiscalProfileRequest request,
                                                                                          HttpServletRequest http) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        TenantFiscalProfile p = profileService.upsert(request, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Perfil fiscal atualizado", map(p)));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<TenantFiscalProfileResponse>> updateProfile(@Valid @RequestBody UpsertTenantFiscalProfileRequest request,
                                                                                  HttpServletRequest http) {
        return createOrUpdateProfile(request, http);
    }

    @GetMapping("/tax-rates")
    public ResponseEntity<ApiResponse<Page<TaxRate>>> listTaxRates(
            @RequestParam(name = "country", required = false, defaultValue = "AO") String country,
            @RequestParam(name = "type", required = false, defaultValue = "VAT") TaxType type,
            @RequestParam(name = "status", required = false) TaxRateStatus status,
            Pageable pageable
    ) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        Page<TaxRate> page = taxRateRepository.list(country, type, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Tax rates", page));
    }

    @GetMapping("/tax-policies")
    public ResponseEntity<ApiResponse<Page<TenantTaxPolicyResponse>>> listPolicies(Pageable pageable) {
        Page<TenantTaxPolicy> page = policyService.list(pageable);
        return ResponseEntity.ok(ApiResponse.success("Tax policies", page.map(this::map)));
    }

    @PostMapping("/tax-policies")
    public ResponseEntity<ApiResponse<TenantTaxPolicyResponse>> createPolicy(@Valid @RequestBody UpsertTenantTaxPolicyRequest request,
                                                                             HttpServletRequest http) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        TenantTaxPolicy p = policyService.create(request, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Tax policy criada", map(p)));
    }

    @PutMapping("/tax-policies/{policyId}")
    public ResponseEntity<ApiResponse<TenantTaxPolicyResponse>> updatePolicy(@PathVariable Long policyId,
                                                                             @Valid @RequestBody UpsertTenantTaxPolicyRequest request,
                                                                             HttpServletRequest http) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        TenantTaxPolicy p = policyService.update(policyId, request, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Tax policy atualizada", map(p)));
    }

    @GetMapping("/product-classifications")
    public ResponseEntity<ApiResponse<Page<ProductTaxClassificationResponse>>> listClassifications(Pageable pageable) {
        Page<ProductTaxClassification> page = classificationService.list(pageable);
        return ResponseEntity.ok(ApiResponse.success("Product classifications", page.map(this::map)));
    }

    @PostMapping("/product-classifications")
    public ResponseEntity<ApiResponse<ProductTaxClassificationResponse>> upsertClassification(@Valid @RequestBody UpsertProductTaxClassificationRequest request,
                                                                                              HttpServletRequest http) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        ProductTaxClassification c = classificationService.createOrUpdate(request, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Product classification upserted", map(c)));
    }

    @PutMapping("/product-classifications/{id}")
    public ResponseEntity<ApiResponse<ProductTaxClassificationResponse>> updateClassification(@PathVariable Long id,
                                                                                              @Valid @RequestBody UpsertProductTaxClassificationRequest request,
                                                                                              HttpServletRequest http) {
        // MVP: id no path é ignorado (upsert por productId) — preserva simplicidade.
        return upsertClassification(request, http);
    }

    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<Page<FiscalDocumentResponse>>> listDocuments(
            @RequestParam(name = "status", required = false) FiscalDocumentStatus status,
            Pageable pageable
    ) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        Page<FiscalDocument> page = fiscalDocumentService
                .listByTenant(ctx.tenantId(), status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Fiscal documents", page.map(this::mapDoc)));
    }

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<ApiResponse<FiscalDocumentResponse>> getDocument(@PathVariable Long documentId) {
        FiscalDocument d = fiscalDocumentService.getForTenant(documentId);
        return ResponseEntity.ok(ApiResponse.success("Fiscal document", d != null ? mapDocWithLines(d) : null));
    }

    @PostMapping("/documents/issue-for-pedido/{pedidoId}")
    public ResponseEntity<ApiResponse<FiscalDocumentResponse>> issueForPedido(@PathVariable Long pedidoId,
                                                                              @RequestParam("pagamentoId") Long pagamentoId,
                                                                              @Valid @RequestBody IssueFiscalDocumentRequest request,
                                                                              HttpServletRequest http) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        FiscalDocument d = fiscalDocumentService.issueForPedidoPaymentAsTenant(pedidoId, pagamentoId, request, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Documento fiscal interno emitido", mapDoc(d)));
    }

    @PostMapping("/documents/{documentId}/cancel")
    public ResponseEntity<ApiResponse<FiscalDocumentResponse>> cancel(@PathVariable Long documentId,
                                                                      @Valid @RequestBody CancelFiscalDocumentRequest request,
                                                                      HttpServletRequest http) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        FiscalDocument d = fiscalDocumentService.cancelAsTenant(documentId, request.getReason(), ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Documento cancelado", mapDoc(d)));
    }

    private TenantFiscalProfileResponse map(TenantFiscalProfile p) {
        TenantFiscalProfileResponse r = new TenantFiscalProfileResponse();
        r.setId(p.getId());
        r.setTenantId(p.getTenant() != null ? p.getTenant().getId() : null);
        r.setStatus(p.getStatus());
        r.setFiscalRegime(p.getFiscalRegime());
        r.setTaxpayerNumber(p.getTaxpayerNumber());
        r.setLegalName(p.getLegalName());
        r.setCommercialName(p.getCommercialName());
        r.setCountryCode(p.getCountryCode());
        r.setProvince(p.getProvince());
        r.setMunicipality(p.getMunicipality());
        r.setAddress(p.getAddress());
        r.setDefaultTaxPolicyId(p.getDefaultTaxPolicy() != null ? p.getDefaultTaxPolicy().getId() : null);
        r.setInvoiceRequired(p.isInvoiceRequired());
        r.setFiscalDocumentEnabled(p.isFiscalDocumentEnabled());
        return r;
    }

    private TenantTaxPolicyResponse map(TenantTaxPolicy p) {
        TenantTaxPolicyResponse r = new TenantTaxPolicyResponse();
        r.setId(p.getId());
        r.setTenantId(p.getTenant() != null ? p.getTenant().getId() : null);
        r.setName(p.getName());
        r.setFiscalRegime(p.getFiscalRegime());
        r.setDefaultTaxRateId(p.getDefaultTaxRate() != null ? p.getDefaultTaxRate().getId() : null);
        r.setPricesIncludeTax(p.isPricesIncludeTax());
        r.setAllowTaxExemptItems(p.isAllowTaxExemptItems());
        r.setRequireTaxDocumentOnPayment(p.isRequireTaxDocumentOnPayment());
        r.setStatus(p.getStatus());
        r.setEffectiveFrom(p.getEffectiveFrom());
        r.setEffectiveTo(p.getEffectiveTo());
        return r;
    }

    private ProductTaxClassificationResponse map(ProductTaxClassification c) {
        ProductTaxClassificationResponse r = new ProductTaxClassificationResponse();
        r.setId(c.getId());
        r.setTenantId(c.getTenant() != null ? c.getTenant().getId() : null);
        r.setProductId(c.getProduct() != null ? c.getProduct().getId() : null);
        r.setTaxRateId(c.getTaxRate() != null ? c.getTaxRate().getId() : null);
        r.setTaxCategory(c.getTaxCategory());
        r.setExemptReason(c.getExemptReason());
        r.setEffectiveFrom(c.getEffectiveFrom());
        r.setEffectiveTo(c.getEffectiveTo());
        r.setStatus(c.getStatus());
        return r;
    }

    private FiscalDocumentResponse mapDoc(FiscalDocument d) {
        FiscalDocumentResponse r = new FiscalDocumentResponse();
        r.setId(d.getId());
        r.setTenantId(d.getTenant() != null ? d.getTenant().getId() : null);
        r.setUnidadeAtendimentoId(d.getUnidadeAtendimento() != null ? d.getUnidadeAtendimento().getId() : null);
        r.setTurnoOperacionalId(d.getTurnoOperacional() != null ? d.getTurnoOperacional().getId() : null);
        r.setPedidoId(d.getPedido() != null ? d.getPedido().getId() : null);
        r.setPagamentoId(d.getPagamento() != null ? d.getPagamento().getId() : null);
        r.setCaixaOperadorSessionId(d.getCaixaOperadorSession() != null ? d.getCaixaOperadorSession().getId() : null);
        r.setDocumentType(d.getDocumentType());
        r.setStatus(d.getStatus());
        r.setFiscalRegime(d.getFiscalRegime());
        r.setDocumentNumber(d.getDocumentNumber());
        r.setSeries(d.getSeries());
        r.setIssuedAt(d.getIssuedAt());
        r.setSubtotalAmount(d.getSubtotalAmount());
        r.setTaxableAmount(d.getTaxableAmount());
        r.setExemptAmount(d.getExemptAmount());
        r.setTaxAmount(d.getTaxAmount());
        r.setTotalAmount(d.getTotalAmount());
        r.setCurrency(d.getCurrency());
        r.setSource(d.getSource());
        r.setCreatedByUserId(d.getCreatedByUser() != null ? d.getCreatedByUser().getId() : null);
        r.setOperationalDeviceId(d.getOperationalDevice() != null ? d.getOperationalDevice().getId() : null);
        return r;
    }

    private FiscalDocumentResponse mapDocWithLines(FiscalDocument d) {
        FiscalDocumentResponse r = mapDoc(d);
        var ctx = tenantGuard.requireContext();
        var lines = fiscalDocumentLineRepository.findByTenantIdAndFiscalDocumentId(ctx.tenantId(), d.getId());
        r.setLines(lines.stream().map(this::mapLine).toList());
        return r;
    }

    private FiscalDocumentResponse.FiscalDocumentLineResponse mapLine(FiscalDocumentLine l) {
        FiscalDocumentResponse.FiscalDocumentLineResponse r = new FiscalDocumentResponse.FiscalDocumentLineResponse();
        r.setId(l.getId());
        r.setProductId(l.getProduct() != null ? l.getProduct().getId() : null);
        r.setPedidoItemId(l.getPedidoItem() != null ? l.getPedidoItem().getId() : null);
        r.setDescription(l.getDescription());
        r.setQuantity(l.getQuantity());
        r.setUnitPrice(l.getUnitPrice());
        r.setNetAmount(l.getNetAmount());
        r.setTaxRateCode(l.getTaxRateCode());
        r.setTaxRateValue(l.getTaxRateValue());
        r.setTaxAmount(l.getTaxAmount());
        r.setGrossAmount(l.getGrossAmount());
        r.setTaxCategory(l.getTaxCategory() != null ? l.getTaxCategory().name() : null);
        r.setExemptReason(l.getExemptReason());
        return r;
    }
}
