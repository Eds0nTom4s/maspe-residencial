package com.restaurante.billing.service;

import com.restaurante.billing.hash.TenantBillingPaymentHashService;
import com.restaurante.billing.repository.TenantBillingInvoiceRepository;
import com.restaurante.billing.repository.TenantBillingPaymentRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.TenantBillingInvoice;
import com.restaurante.model.entity.TenantBillingPayment;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantBillingCollectionStatus;
import com.restaurante.model.enums.TenantBillingInvoiceStatus;
import com.restaurante.model.enums.TenantBillingPaymentStatus;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static com.restaurante.billing.util.BillingMath.nz;
import static com.restaurante.billing.util.BillingMath.scaleMoney;

@Service
@RequiredArgsConstructor
public class TenantBillingPaymentService {

    private static final EnumSet<TenantBillingInvoiceStatus> PAYABLE_STATUSES = EnumSet.of(
            TenantBillingInvoiceStatus.ISSUED,
            TenantBillingInvoiceStatus.PARTIALLY_PAID,
            TenantBillingInvoiceStatus.OVERDUE
    );

    private final TenantBillingInvoiceRepository invoiceRepository;
    private final TenantBillingPaymentRepository paymentRepository;
    private final TenantBillingPaymentSequenceService sequenceService;
    private final TenantBillingPaymentHashService paymentHashService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public TenantBillingPayment recordPayment(Long tenantId,
                                              Long invoiceId,
                                              BigDecimal amount,
                                              String currency,
                                              com.restaurante.model.enums.TenantBillingPaymentMethod method,
                                              LocalDateTime paidAt,
                                              String reference,
                                              String proofReference,
                                              String notes,
                                              User recordedByUser,
                                              boolean confirmNow) {
        TenantBillingInvoice inv = invoiceRepository.findByTenantIdAndId(tenantId, invoiceId)
                .orElseThrow(() -> new BusinessException("TENANT_BILLING_INVOICE_NOT_FOUND"));

        if (!PAYABLE_STATUSES.contains(inv.getStatus())) throw new BusinessException("TENANT_BILLING_INVOICE_NOT_PAYABLE");
        if (inv.getStatus() == TenantBillingInvoiceStatus.CANCELLED || inv.getStatus() == TenantBillingInvoiceStatus.VOIDED) throw new BusinessException("TENANT_BILLING_INVOICE_CANCELLED");

        BigDecimal amt = scaleMoney(nz(amount));
        if (amt.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("TENANT_BILLING_PAYMENT_AMOUNT_INVALID");

        String cur = currency != null ? currency.trim() : null;
        if (cur == null || cur.length() != 3) throw new BusinessException("TENANT_BILLING_PAYMENT_CURRENCY_MISMATCH");
        if (inv.getCurrency() != null && !inv.getCurrency().equalsIgnoreCase(cur)) throw new BusinessException("TENANT_BILLING_PAYMENT_CURRENCY_MISMATCH");

        BigDecimal outstanding = scaleMoney(nz(inv.getOutstandingAmount()));
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("TENANT_BILLING_INVOICE_ALREADY_PAID");
        if (amt.compareTo(outstanding) > 0) throw new BusinessException("TENANT_BILLING_PAYMENT_OVERPAYMENT_NOT_ALLOWED");

        TenantBillingPayment p = new TenantBillingPayment();
        p.setTenant(inv.getTenant());
        p.setInvoice(inv);
        p.setBillingCycle(inv.getBillingCycle());
        p.setSubscription(inv.getSubscription());
        p.setPaymentNumber(sequenceService.nextNumber(tenantId, LocalDateTime.now()));
        p.setPaymentMethod(method);
        p.setAmount(amt);
        p.setCurrency(inv.getCurrency());
        p.setPaidAt(paidAt);
        p.setReceivedAt(LocalDateTime.now());
        p.setReference(reference);
        p.setProofReference(proofReference);
        p.setNotes(notes);
        p.setRecordedByUser(recordedByUser);
        p.setStatus(confirmNow ? TenantBillingPaymentStatus.CONFIRMED : TenantBillingPaymentStatus.RECORDED);
        if (confirmNow) p.setConfirmedAt(LocalDateTime.now());

        p = paymentRepository.save(p);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.TENANT_BILLING_PAYMENT_RECORDED,
                OperationalEntityType.TENANT_BILLING_PAYMENT,
                p.getId(),
                OperationalOrigem.TENANT_FINANCE,
                "Pagamento de invoice registrado",
                Map.of(
                        "tenantId", tenantId,
                        "invoiceId", inv.getId(),
                        "paymentId", p.getId(),
                        "paymentNumber", p.getPaymentNumber(),
                        "amount", p.getAmount(),
                        "currency", p.getCurrency(),
                        "status", p.getStatus().name(),
                        "paymentHash", paymentHashService.hash(p)
                ),
                null,
                null
        );

        if (confirmNow) {
            recalculateInvoicePaymentStatus(tenantId, inv.getId(), LocalDateTime.now());
        }

        return p;
    }

    @Transactional
    public TenantBillingPayment confirmPayment(Long tenantId, Long paymentId) {
        TenantBillingPayment p = paymentRepository.findByTenantIdAndId(tenantId, paymentId)
                .orElseThrow(() -> new BusinessException("TENANT_BILLING_PAYMENT_NOT_FOUND"));
        if (p.getStatus() != TenantBillingPaymentStatus.RECORDED && p.getStatus() != TenantBillingPaymentStatus.DRAFT) {
            throw new BusinessException("TENANT_BILLING_PAYMENT_INVALID_STATE");
        }
        p.setStatus(TenantBillingPaymentStatus.CONFIRMED);
        p.setConfirmedAt(LocalDateTime.now());
        p = paymentRepository.save(p);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.TENANT_BILLING_PAYMENT_CONFIRMED,
                OperationalEntityType.TENANT_BILLING_PAYMENT,
                p.getId(),
                OperationalOrigem.TENANT_FINANCE,
                "Pagamento confirmado",
                Map.of("tenantId", tenantId, "paymentId", p.getId(), "invoiceId", p.getInvoice() != null ? p.getInvoice().getId() : null),
                null,
                null
        );

        if (p.getInvoice() != null) {
            recalculateInvoicePaymentStatus(tenantId, p.getInvoice().getId(), LocalDateTime.now());
        }
        return p;
    }

    @Transactional
    public TenantBillingPayment rejectPayment(Long tenantId, Long paymentId, String reason) {
        TenantBillingPayment p = paymentRepository.findByTenantIdAndId(tenantId, paymentId)
                .orElseThrow(() -> new BusinessException("TENANT_BILLING_PAYMENT_NOT_FOUND"));
        if (p.getStatus() == TenantBillingPaymentStatus.CONFIRMED) throw new BusinessException("TENANT_BILLING_PAYMENT_INVALID_STATE");
        if (p.getStatus() == TenantBillingPaymentStatus.REJECTED) return p;
        p.setStatus(TenantBillingPaymentStatus.REJECTED);
        p.setNotes(reason);
        p = paymentRepository.save(p);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.TENANT_BILLING_PAYMENT_REJECTED,
                OperationalEntityType.TENANT_BILLING_PAYMENT,
                p.getId(),
                OperationalOrigem.TENANT_FINANCE,
                "Pagamento rejeitado",
                Map.of("tenantId", tenantId, "paymentId", p.getId()),
                null,
                null
        );
        return p;
    }

    @Transactional
    public TenantBillingPayment cancelPayment(Long tenantId, Long paymentId, String reason) {
        TenantBillingPayment p = paymentRepository.findByTenantIdAndId(tenantId, paymentId)
                .orElseThrow(() -> new BusinessException("TENANT_BILLING_PAYMENT_NOT_FOUND"));
        if (p.getStatus() == TenantBillingPaymentStatus.CONFIRMED) throw new BusinessException("TENANT_BILLING_PAYMENT_INVALID_STATE");
        if (p.getStatus() == TenantBillingPaymentStatus.CANCELLED) return p;
        p.setStatus(TenantBillingPaymentStatus.CANCELLED);
        p.setNotes(reason);
        p = paymentRepository.save(p);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.TENANT_BILLING_PAYMENT_CANCELLED,
                OperationalEntityType.TENANT_BILLING_PAYMENT,
                p.getId(),
                OperationalOrigem.TENANT_FINANCE,
                "Pagamento cancelado",
                Map.of("tenantId", tenantId, "paymentId", p.getId()),
                null,
                null
        );
        return p;
    }

    @Transactional
    public TenantBillingInvoice recalculateInvoicePaymentStatus(Long tenantId, Long invoiceId, LocalDateTime at) {
        TenantBillingInvoice inv = invoiceRepository.findByTenantIdAndId(tenantId, invoiceId)
                .orElseThrow(() -> new BusinessException("TENANT_BILLING_INVOICE_NOT_FOUND"));

        List<TenantBillingPayment> confirmed = paymentRepository.findByTenantIdAndInvoice_IdAndStatusInOrderByIdAsc(
                tenantId,
                invoiceId,
                List.of(TenantBillingPaymentStatus.CONFIRMED)
        );

        BigDecimal totalPaid = BigDecimal.ZERO;
        LocalDateTime lastPaidAt = null;
        for (TenantBillingPayment p : confirmed) {
            totalPaid = totalPaid.add(nz(p.getAmount()));
            if (p.getConfirmedAt() != null && (lastPaidAt == null || p.getConfirmedAt().isAfter(lastPaidAt))) {
                lastPaidAt = p.getConfirmedAt();
            }
        }

        totalPaid = scaleMoney(totalPaid);
        BigDecimal total = scaleMoney(nz(inv.getTotalAmount()));
        BigDecimal outstanding = scaleMoney(total.subtract(totalPaid));
        if (outstanding.compareTo(BigDecimal.ZERO) < 0) outstanding = BigDecimal.ZERO;

        inv.setTotalPaidAmount(totalPaid);
        inv.setOutstandingAmount(outstanding);
        inv.setLastPaymentAt(lastPaidAt);

        TenantBillingInvoiceStatus previousStatus = inv.getStatus();
        TenantBillingCollectionStatus previousCollection = inv.getCollectionStatus();

        if (outstanding.compareTo(BigDecimal.ZERO) == 0) {
            inv.setStatus(TenantBillingInvoiceStatus.PAID);
            if (inv.getPaidAt() == null) inv.setPaidAt(at != null ? at : LocalDateTime.now());
            inv.setCollectionStatus(TenantBillingCollectionStatus.CLEARED);
            inv.setOverdueAt(null);
            inv.setGracePeriodEndsAt(null);
            operationalEventLogService.logGenericForTenant(
                    tenantId,
                    OperationalEventType.TENANT_BILLING_INVOICE_PAID,
                    OperationalEntityType.TENANT_BILLING_INVOICE,
                    inv.getId(),
                    OperationalOrigem.TENANT_FINANCE,
                    "Invoice paga",
                    Map.of("tenantId", tenantId, "invoiceId", inv.getId(), "previousStatus", previousStatus != null ? previousStatus.name() : null),
                    null,
                    null
            );
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            inv.setStatus(TenantBillingInvoiceStatus.PARTIALLY_PAID);
            operationalEventLogService.logGenericForTenant(
                    tenantId,
                    OperationalEventType.TENANT_BILLING_INVOICE_PARTIALLY_PAID,
                    OperationalEntityType.TENANT_BILLING_INVOICE,
                    inv.getId(),
                    OperationalOrigem.TENANT_FINANCE,
                    "Invoice parcialmente paga",
                    Map.of("tenantId", tenantId, "invoiceId", inv.getId(), "outstandingAmount", outstanding),
                    null,
                    null
            );
        } else if (inv.getStatus() == TenantBillingInvoiceStatus.PAID) {
            // Should not happen in MVP (no refunds), keep PAID
            inv.setStatus(TenantBillingInvoiceStatus.PAID);
        } else if (inv.getStatus() == TenantBillingInvoiceStatus.DRAFT) {
            // keep
        } else {
            inv.setStatus(TenantBillingInvoiceStatus.ISSUED);
        }

        if (previousStatus != inv.getStatus() || previousCollection != inv.getCollectionStatus()) {
            operationalEventLogService.logGenericForTenant(
                    tenantId,
                    OperationalEventType.TENANT_BILLING_COLLECTION_STATUS_CHANGED,
                    OperationalEntityType.TENANT_BILLING_INVOICE,
                    inv.getId(),
                    OperationalOrigem.TENANT_FINANCE,
                    "Collection status atualizado",
                    Map.of(
                            "tenantId", tenantId,
                            "invoiceId", inv.getId(),
                            "statusBefore", previousStatus != null ? previousStatus.name() : null,
                            "statusAfter", inv.getStatus() != null ? inv.getStatus().name() : null,
                            "collectionBefore", previousCollection != null ? previousCollection.name() : null,
                            "collectionAfter", inv.getCollectionStatus() != null ? inv.getCollectionStatus().name() : null
                    ),
                    null,
                    null
            );
        }

        return invoiceRepository.save(inv);
    }
}

