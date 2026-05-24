package com.restaurante.fiscal.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.fiscal.config.TaxProperties;
import com.restaurante.fiscal.dto.TaxCalculationLineResult;
import com.restaurante.fiscal.dto.TaxCalculationResult;
import com.restaurante.fiscal.repository.ProductTaxClassificationRepository;
import com.restaurante.fiscal.repository.TaxRateRepository;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.fiscal.repository.TenantTaxPolicyRepository;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.ProductTaxClassification;
import com.restaurante.model.entity.TaxRate;
import com.restaurante.model.entity.TenantFiscalProfile;
import com.restaurante.model.entity.TenantTaxPolicy;
import com.restaurante.model.enums.FiscalRegime;
import com.restaurante.model.enums.TaxCategory;
import com.restaurante.model.enums.TaxRateStatus;
import com.restaurante.model.enums.TaxType;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TaxCalculationService {

    private final TaxProperties props;
    private final TenantGuard tenantGuard;
    private final PedidoRepository pedidoRepository;
    private final TenantFiscalProfileRepository fiscalProfileRepository;
    private final TenantTaxPolicyRepository taxPolicyRepository;
    private final ProductTaxClassificationRepository productTaxClassificationRepository;
    private final TaxRateRepository taxRateRepository;

    @Transactional(readOnly = true)
    public TaxCalculationResult calculateForPedido(Long tenantId, Long pedidoId, LocalDateTime operationDate) {
        if (!props.isEnabled()) {
            throw new BusinessException("Tax module desativado.");
        }
        if (tenantId == null) throw new BusinessException("tenantId é obrigatório.");
        if (pedidoId == null) throw new BusinessException("pedidoId é obrigatório.");
        LocalDateTime at = operationDate != null ? operationDate : LocalDateTime.now();

        Pedido pedido = pedidoRepository.findByIdAndTenantIdComItensESubPedidos(pedidoId, tenantId)
                .orElseThrow(() -> new BusinessException("Pedido não encontrado."));
        tenantGuard.assertResourceBelongsToTenant(pedido.getTenant().getId());

        TenantFiscalProfile profile = fiscalProfileRepository.findByTenantId(tenantId).orElse(null);
        if (profile == null) {
            profile = new TenantFiscalProfile();
            profile.setTenant(pedido.getTenant());
            profile.setFiscalRegime(FiscalRegime.NOT_CONFIGURED);
        }

        TenantTaxPolicy policy = resolvePolicy(tenantId, profile, at);

        TaxCalculationResult out = new TaxCalculationResult();
        out.setTenantId(tenantId);
        out.setPedidoId(pedidoId);
        out.setFiscalRegime(policy != null ? policy.getFiscalRegime() : profile.getFiscalRegime());
        out.setPricesIncludeTax(policy != null && policy.isPricesIncludeTax());

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal exempt = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (ItemPedido item : pedido.getItens()) {
            if (item == null) continue;
            BigDecimal qty = BigDecimal.valueOf(item.getQuantidade() != null ? item.getQuantidade() : 0);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            ProductTaxClassification classification = productTaxClassificationRepository
                    .findActiveEffectiveByTenantAndProduct(tenantId, item.getProduto().getId(), at)
                    .orElse(null);

            if (classification == null && props.getDocument().isRequireProductClassification()) {
                throw new BusinessException("Classificação fiscal obrigatória em produto: " + item.getProduto().getId());
            }

            TaxRate rate = resolveTaxRate(policy, classification, at);
            BigDecimal rateValue = rate != null ? nz(rate.getRate()) : BigDecimal.ZERO;

            boolean isExempt = classification != null && (classification.getTaxCategory() == TaxCategory.EXEMPT || classification.getTaxCategory() == TaxCategory.OUT_OF_SCOPE);
            if (policy != null && !policy.isAllowTaxExemptItems() && isExempt) {
                throw new BusinessException("Itens isentos não permitidos pela política fiscal do tenant.");
            }

            BigDecimal unitPrice = nz(item.getPrecoUnitario());
            BigDecimal grossLine;
            BigDecimal netLine;
            BigDecimal taxLine;

            if (out.isPricesIncludeTax()) {
                grossLine = unitPrice.multiply(qty);
                BigDecimal divisor = BigDecimal.ONE.add(rateValue.divide(new BigDecimal("100"), props.getCalculationScale(), props.getRoundingMode()));
                netLine = grossLine.divide(divisor, props.getCalculationScale(), props.getRoundingMode());
                taxLine = grossLine.subtract(netLine);
            } else {
                netLine = unitPrice.multiply(qty);
                taxLine = netLine.multiply(rateValue).divide(new BigDecimal("100"), props.getCalculationScale(), props.getRoundingMode());
                grossLine = netLine.add(taxLine);
            }

            netLine = money(netLine);
            grossLine = money(grossLine);
            taxLine = money(taxLine);

            TaxCalculationLineResult line = new TaxCalculationLineResult();
            line.setPedidoItemId(item.getId());
            line.setProductId(item.getProduto() != null ? item.getProduto().getId() : null);
            line.setQuantity(item.getQuantidade());
            line.setUnitPrice(money(unitPrice));
            line.setNetAmount(netLine);
            line.setTaxRateId(rate != null ? rate.getId() : null);
            line.setTaxRateCode(rate != null ? rate.getCode() : null);
            line.setTaxRateValue(rate != null ? rateValue : null);
            line.setTaxAmount(isExempt ? BigDecimal.ZERO : taxLine);
            line.setGrossAmount(isExempt ? netLine : grossLine);
            line.setTaxCategory(classification != null ? classification.getTaxCategory() : TaxCategory.STANDARD);
            line.setExemptReason(classification != null ? classification.getExemptReason() : null);
            out.getLines().add(line);

            subtotal = subtotal.add(netLine);
            if (isExempt || rateValue.compareTo(BigDecimal.ZERO) == 0) {
                exempt = exempt.add(netLine);
            } else {
                taxable = taxable.add(netLine);
                tax = tax.add(line.getTaxAmount());
            }
            total = total.add(line.getGrossAmount());
        }

        out.setSubtotalAmount(money(subtotal));
        out.setTaxableAmount(money(taxable));
        out.setExemptAmount(money(exempt));
        out.setTaxAmount(money(tax));
        out.setTotalAmount(money(total));

        return out;
    }

    private TenantTaxPolicy resolvePolicy(Long tenantId, TenantFiscalProfile profile, LocalDateTime at) {
        if (profile != null && profile.getDefaultTaxPolicy() != null && profile.getDefaultTaxPolicy().getId() != null) {
            TenantTaxPolicy p = profile.getDefaultTaxPolicy();
            if (p.getTenant() != null && p.getTenant().getId() != null && !p.getTenant().getId().equals(tenantId)) {
                throw new BusinessException("defaultTaxPolicy cross-tenant.");
            }
            return p;
        }
        return taxPolicyRepository.findActiveEffective(tenantId, at).orElse(null);
    }

    private TaxRate resolveTaxRate(TenantTaxPolicy policy, ProductTaxClassification classification, LocalDateTime at) {
        Long taxRateId = null;
        if (classification != null && classification.getTaxRate() != null) taxRateId = classification.getTaxRate().getId();
        if (taxRateId == null && policy != null && policy.getDefaultTaxRate() != null) taxRateId = policy.getDefaultTaxRate().getId();
        if (taxRateId == null) {
            // fallback: padrão AO se existir
            return taxRateRepository.findByCountryCodeAndCode(props.getDefaultCountry(), "AO_VAT_STANDARD_14").orElse(null);
        }
        TaxRate r = taxRateRepository.findEffectiveById(taxRateId, at).orElse(null);
        if (r == null) {
            throw new BusinessException("TaxRate não efetiva na data da operação.");
        }
        if (r.getStatus() == TaxRateStatus.INACTIVE) {
            throw new BusinessException("TaxRate inativa.");
        }
        if (r.getTaxType() != TaxType.VAT) {
            // MVP: apenas VAT para cálculo aqui
            return r;
        }
        return r;
    }

    private BigDecimal money(BigDecimal v) {
        if (v == null) return BigDecimal.ZERO.setScale(props.getMonetaryScale(), props.getRoundingMode());
        return v.setScale(props.getMonetaryScale(), props.getRoundingMode());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
