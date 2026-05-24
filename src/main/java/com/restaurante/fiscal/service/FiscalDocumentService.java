package com.restaurante.fiscal.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.dto.request.IssueFiscalDocumentRequest;
import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.fiscal.config.TaxProperties;
import com.restaurante.fiscal.dto.TaxCalculationResult;
import com.restaurante.fiscal.repository.FiscalDocumentLineRepository;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.fiscal.repository.TaxRateRepository;
import com.restaurante.model.entity.CaixaOperadorSession;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.FiscalDocumentLine;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.TaxRate;
import com.restaurante.model.entity.TenantFiscalProfile;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.FiscalDocumentSource;
import com.restaurante.model.enums.FiscalDocumentStatus;
import com.restaurante.model.enums.FiscalDocumentType;
import com.restaurante.model.enums.FiscalRegime;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class FiscalDocumentService {

    private final TaxProperties props;
    private final TenantGuard tenantGuard;
    private final TenantFiscalProfileRepository fiscalProfileRepository;
    private final PedidoRepository pedidoRepository;
    private final PagamentoGatewayRepository pagamentoGatewayRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final FiscalDocumentLineRepository fiscalDocumentLineRepository;
    private final TaxCalculationService taxCalculationService;
    private final FiscalDocumentSequenceService sequenceService;
    private final TaxRateRepository taxRateRepository;
    private final UserRepository userRepository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public FiscalDocument issueForPedidoPaymentAsTenant(Long pedidoId, Long pagamentoId, IssueFiscalDocumentRequest request, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        return issueInternal(ctx.tenantId(), pedidoId, pagamentoId, request, FiscalDocumentSource.ADMIN, ctx.userId(), null, ip, userAgent);
    }

    @Transactional
    public FiscalDocument issueForPedidoPaymentAsDevice(DevicePrincipal device, Long pedidoId, Long pagamentoId, IssueFiscalDocumentRequest request, String ip, String userAgent) {
        if (device == null) throw new IllegalStateException("DevicePrincipal obrigatório.");
        if (device.capabilities() == null || !device.capabilities().contains(com.restaurante.model.enums.DeviceCapability.ISSUE_FISCAL_DOCUMENT)) {
            throw new DeviceApiException(HttpStatus.FORBIDDEN,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_FORBIDDEN,
                    "Capability requerida: ISSUE_FISCAL_DOCUMENT",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
        return issueInternal(device.tenantId(), pedidoId, pagamentoId, request, FiscalDocumentSource.POS, null, device.dispositivoId(), ip, userAgent);
    }

    /**
     * Emissão automática (worker/server-side) pós-pagamento confirmado.
     *
     * Importante:
     * - Não exige TenantUserRole (não há usuário).
     * - Roda com TenantContext temporário (platformAdmin=true) para reutilizar validações tenant-safe.
     */
    @Transactional
    public FiscalDocument issueForPedidoPaymentAsSystem(Long tenantId, Long pedidoId, Long pagamentoId) {
        if (tenantId == null) throw new BusinessException("tenantId é obrigatório.");
        TenantContextHolder.set(new TenantContext(
                tenantId,
                null,
                null,
                Set.of(),
                TenantResolutionSource.LEGACY_NONE,
                true,
                false
        ));
        try {
            return issueInternal(tenantId, pedidoId, pagamentoId, null, FiscalDocumentSource.SYSTEM, null, null, null, null);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Transactional
    public FiscalDocument cancelAsTenant(Long documentId, String reason, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        FiscalDocument doc = fiscalDocumentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new BusinessException("Documento fiscal não encontrado."));
        tenantGuard.assertResourceBelongsToTenant(doc.getTenant().getId());

        if (doc.getStatus() != FiscalDocumentStatus.ISSUED) {
            throw new BusinessException("Apenas documentos ISSUED podem ser cancelados.");
        }
        doc.setStatus(FiscalDocumentStatus.CANCELLED);
        fiscalDocumentRepository.save(doc);

        operationalEventLogService.logGeneric(
                OperationalEventType.FISCAL_DOCUMENT_CANCELLED,
                OperationalEntityType.FISCAL_DOCUMENT,
                doc.getId(),
                OperationalOrigem.SYSTEM,
                "Documento fiscal interno cancelado",
                Map.of(
                        "documentId", doc.getId(),
                        "pedidoId", doc.getPedido() != null ? doc.getPedido().getId() : null,
                        "pagamentoId", doc.getPagamento() != null ? doc.getPagamento().getId() : null,
                        "reasonPresent", reason != null && !reason.isBlank()
                ),
                ip,
                userAgent
        );

        return doc;
    }

    @Transactional(readOnly = true)
    public FiscalDocument getForTenant(Long documentId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        FiscalDocument doc = fiscalDocumentRepository.findById(documentId).orElse(null);
        if (doc == null || doc.getTenant() == null || !doc.getTenant().getId().equals(ctx.tenantId())) return null;
        return doc;
    }

    @Transactional(readOnly = true)
    public Page<FiscalDocument> listByTenant(Long tenantId, FiscalDocumentStatus status, Pageable pageable) {
        if (tenantId == null) throw new BusinessException("tenantId é obrigatório.");
        return fiscalDocumentRepository.listByTenant(tenantId, status, pageable);
    }

    @Transactional(readOnly = true)
    public FiscalDocument getForDevice(DevicePrincipal device, Long documentId) {
        if (device == null) throw new IllegalStateException("DevicePrincipal obrigatório.");
        if (device.capabilities() == null || !device.capabilities().contains(com.restaurante.model.enums.DeviceCapability.VIEW_FISCAL_DOCUMENT)) {
            throw new DeviceApiException(HttpStatus.FORBIDDEN,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_FORBIDDEN,
                    "Capability requerida: VIEW_FISCAL_DOCUMENT",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
        FiscalDocument doc = fiscalDocumentRepository.findById(documentId).orElse(null);
        if (doc == null || doc.getTenant() == null || !doc.getTenant().getId().equals(device.tenantId())) return null;
        // unidade-safe (quando doc possui unidade)
        if (doc.getUnidadeAtendimento() != null && device.unidadeAtendimentoId() != null
                && !doc.getUnidadeAtendimento().getId().equals(device.unidadeAtendimentoId())) {
            return null;
        }
        return doc;
    }

    private FiscalDocument issueInternal(Long tenantId,
                                        Long pedidoId,
                                        Long pagamentoId,
                                        IssueFiscalDocumentRequest request,
                                        FiscalDocumentSource source,
                                        Long createdByUserId,
                                        Long deviceId,
                                        String ip,
                                        String userAgent) {
        if (!props.isEnabled()) throw new BusinessException("Tax module desativado.");
        if (tenantId == null) throw new BusinessException("tenantId é obrigatório.");
        if (pedidoId == null) throw new BusinessException("pedidoId é obrigatório.");
        if (pagamentoId == null) throw new BusinessException("pagamentoId é obrigatório.");

        TenantFiscalProfile profile = fiscalProfileRepository.findByTenantId(tenantId).orElse(null);
        if (profile == null || profile.getFiscalRegime() == FiscalRegime.NOT_CONFIGURED) {
            throw new BusinessException("TenantFiscalProfile não configurado.");
        }
        if (profile.getStatus() != com.restaurante.model.enums.TenantFiscalProfileStatus.ACTIVE) {
            throw new BusinessException("TenantFiscalProfile não está ACTIVE.");
        }
        if (!profile.isFiscalDocumentEnabled()) {
            throw new BusinessException("Documento fiscal interno está desativado para o tenant.");
        }

        Pedido pedido = pedidoRepository.findByIdAndTenantIdComItensESubPedidos(pedidoId, tenantId)
                .orElseThrow(() -> new BusinessException("Pedido não encontrado."));
        tenantGuard.assertResourceBelongsToTenant(pedido.getTenant().getId());

        Pagamento pagamento = pagamentoGatewayRepository.findByIdAndTenantId(pagamentoId, tenantId)
                .orElseThrow(() -> new BusinessException("Pagamento não encontrado."));
        if (pagamento.getStatus() != StatusPagamentoGateway.CONFIRMADO || pagamento.getConfirmedAt() == null) {
            throw new BusinessException("Pagamento não está CONFIRMADO.");
        }
        if (pagamento.getPedido() == null || !pagamento.getPedido().getId().equals(pedido.getId())) {
            throw new BusinessException("Pagamento não pertence ao pedido.");
        }
        if (pedido.getStatusFinanceiro() != StatusFinanceiroPedido.PAGO) {
            // tolerante: pagamento confirmado deveria implicar pedido pago, mas não vamos acoplar.
        }

        FiscalDocument existing = fiscalDocumentRepository.findByTenantIdAndPagamentoId(tenantId, pagamentoId).orElse(null);
        if (existing != null) return existing;

        TaxCalculationResult calc = taxCalculationService.calculateForPedido(tenantId, pedidoId, pagamento.getConfirmedAt());

        FiscalDocumentType docType = request != null && request.getDocumentType() != null ? request.getDocumentType() : FiscalDocumentType.INTERNAL_RECEIPT;
        String series = request != null && request.getSeries() != null && !request.getSeries().isBlank() ? request.getSeries().trim() : "A";
        LocalDateTime issuedAt = LocalDateTime.now();
        String number = sequenceService.nextNumber(tenantId,
                pedido.getSessaoConsumo() != null && pedido.getSessaoConsumo().getUnidadeAtendimento() != null ? pedido.getSessaoConsumo().getUnidadeAtendimento().getId() : null,
                docType,
                series,
                issuedAt);

        FiscalDocument doc = new FiscalDocument();
        doc.setTenant(pedido.getTenant());
        doc.setInstituicao(pedido.getSessaoConsumo() != null ? pedido.getSessaoConsumo().getInstituicao() : null);
        doc.setUnidadeAtendimento(pedido.getSessaoConsumo() != null ? pedido.getSessaoConsumo().getUnidadeAtendimento() : null);
        doc.setTurnoOperacional(pedido.getTurnoOperacional());
        doc.setSessaoConsumo(pedido.getSessaoConsumo());
        doc.setPedido(pedido);
        doc.setPagamento(pagamento);
        CaixaOperadorSession caixa = pagamento.getOrdemPagamento() != null ? pagamento.getOrdemPagamento().getCaixaOperadorSession() : null;
        doc.setCaixaOperadorSession(caixa);
        doc.setDocumentType(docType);
        doc.setStatus(FiscalDocumentStatus.ISSUED);
        doc.setFiscalRegime(profile.getFiscalRegime());
        doc.setDocumentNumber(number);
        doc.setSeries(series);
        doc.setIssuedAt(issuedAt);
        doc.setCustomerName(trimToNull(request != null ? request.getCustomerName() : null));
        doc.setCustomerTaxpayerNumber(trimToNull(request != null ? request.getCustomerTaxpayerNumber() : null));
        doc.setSubtotalAmount(calc.getSubtotalAmount());
        doc.setTaxableAmount(calc.getTaxableAmount());
        doc.setExemptAmount(calc.getExemptAmount());
        doc.setTaxAmount(calc.getTaxAmount());
        doc.setTotalAmount(calc.getTotalAmount());
        doc.setCurrency("AOA");
        doc.setSource(source != null ? source : FiscalDocumentSource.SYSTEM);
        if (createdByUserId != null) {
            User u = userRepository.findById(createdByUserId).orElse(null);
            doc.setCreatedByUser(u);
        }
        if (deviceId != null) {
            // vínculo do dispositivo operacional é opcional; já está presente em Pagamento/Ordem
            // manter null se não houver repositório aqui
        }

        try {
            doc = fiscalDocumentRepository.save(doc);
        } catch (DataIntegrityViolationException ex) {
            FiscalDocument already = fiscalDocumentRepository.findByTenantIdAndPagamentoId(tenantId, pagamentoId).orElse(null);
            if (already != null) return already;
            throw ex;
        }

        for (var line : calc.getLines()) {
            ItemPedido item = findItem(pedido, line.getPedidoItemId());
            Produto prod = item != null ? item.getProduto() : null;
            TaxRate rate = line.getTaxRateId() != null ? taxRateRepository.findById(line.getTaxRateId()).orElse(null) : null;

            FiscalDocumentLine l = new FiscalDocumentLine();
            l.setFiscalDocument(doc);
            l.setTenant(doc.getTenant());
            l.setPedidoItem(item);
            l.setProduct(prod);
            l.setTaxRate(rate);
            l.setDescription(safeDesc(line.getPedidoItemId(), pedido));
            l.setQuantity(line.getQuantity());
            l.setUnitPrice(line.getUnitPrice());
            l.setNetAmount(line.getNetAmount());
            l.setTaxRateCode(line.getTaxRateCode());
            l.setTaxRateValue(line.getTaxRateValue());
            l.setTaxAmount(line.getTaxAmount());
            l.setGrossAmount(line.getGrossAmount());
            l.setTaxCategory(line.getTaxCategory());
            l.setExemptReason(trimToNull(line.getExemptReason()));
            fiscalDocumentLineRepository.save(l);
        }

        operationalEventLogService.logGeneric(
                OperationalEventType.FISCAL_DOCUMENT_ISSUED,
                OperationalEntityType.FISCAL_DOCUMENT,
                doc.getId(),
                source == FiscalDocumentSource.POS ? OperationalOrigem.DEVICE_POS : OperationalOrigem.SYSTEM,
                "Documento fiscal interno emitido",
                Map.of(
                        "documentId", doc.getId(),
                        "documentType", doc.getDocumentType().name(),
                        "documentNumber", doc.getDocumentNumber(),
                        "series", doc.getSeries(),
                        "pedidoId", pedido.getId(),
                        "pagamentoId", pagamento.getId(),
                        "taxAmount", doc.getTaxAmount(),
                        "totalAmount", doc.getTotalAmount(),
                        "fiscalRegime", doc.getFiscalRegime().name()
                ),
                ip,
                userAgent
        );

        return doc;
    }

    private static String safeDesc(Long pedidoItemId, Pedido pedido) {
        if (pedidoItemId == null || pedido == null || pedido.getItens() == null) return "Item";
        return pedido.getItens().stream()
                .filter(i -> i != null && i.getId() != null && i.getId().equals(pedidoItemId))
                .map(i -> i.getProduto() != null ? i.getProduto().getNome() : "Item")
                .findFirst()
                .orElse("Item");
    }

    private static ItemPedido findItem(Pedido pedido, Long itemId) {
        if (itemId == null || pedido == null || pedido.getItens() == null) return null;
        return pedido.getItens().stream()
                .filter(i -> i != null && i.getId() != null && i.getId().equals(itemId))
                .findFirst()
                .orElse(null);
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
