package com.restaurante.controller;

import com.restaurante.dto.request.IssueFiscalDocumentRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.FiscalDocumentResponse;
import com.restaurante.fiscal.repository.FiscalDocumentLineRepository;
import com.restaurante.fiscal.service.FiscalDocumentService;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.FiscalDocumentLine;
import com.restaurante.security.device.DevicePrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/device/fiscal/documents")
@RequiredArgsConstructor
@Tag(name = "Device - Fiscal", description = "Emissão/consulta de documento fiscal interno (device/POS)")
public class DeviceFiscalDocumentController {

    private final FiscalDocumentService fiscalDocumentService;
    private final FiscalDocumentLineRepository fiscalDocumentLineRepository;

    @GetMapping("/{documentId}")
    public ResponseEntity<ApiResponse<FiscalDocumentResponse>> get(@PathVariable Long documentId) {
        DevicePrincipal device = requireDevicePrincipal();
        FiscalDocument d = fiscalDocumentService.getForDevice(device, documentId);
        if (d == null) return ResponseEntity.ok(ApiResponse.success("Fiscal document", null));
        return ResponseEntity.ok(ApiResponse.success("Fiscal document", mapDocWithLines(device, d)));
    }

    @PostMapping("/issue-for-pedido/{pedidoId}")
    public ResponseEntity<ApiResponse<FiscalDocumentResponse>> issueForPedido(@PathVariable Long pedidoId,
                                                                              @RequestParam("pagamentoId") Long pagamentoId,
                                                                              @Valid @RequestBody IssueFiscalDocumentRequest request,
                                                                              HttpServletRequest http) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        FiscalDocument d = fiscalDocumentService.issueForPedidoPaymentAsDevice(device, pedidoId, pagamentoId, request, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Documento fiscal interno emitido", mapDocWithLines(device, d)));
    }

    private FiscalDocumentResponse mapDocWithLines(DevicePrincipal device, FiscalDocument d) {
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

        var lines = fiscalDocumentLineRepository.findByTenantIdAndFiscalDocumentId(device.tenantId(), d.getId());
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

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        if (principal instanceof DevicePrincipal dp) return dp;
        throw new IllegalStateException("DevicePrincipal obrigatório.");
    }
}

