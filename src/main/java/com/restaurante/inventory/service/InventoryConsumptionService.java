package com.restaurante.inventory.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.inventory.config.InventoryProperties;
import com.restaurante.inventory.repository.InventoryConsumptionLineRepository;
import com.restaurante.inventory.repository.InventoryConsumptionRecordRepository;
import com.restaurante.inventory.repository.InventoryItemRepository;
import com.restaurante.inventory.repository.InventoryMovementRepository;
import com.restaurante.inventory.repository.InventoryRecipeLineRepository;
import com.restaurante.inventory.repository.InventoryRecipeRepository;
import com.restaurante.inventory.repository.ProductInventoryMappingRepository;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.InventoryConsumptionLine;
import com.restaurante.model.entity.InventoryConsumptionRecord;
import com.restaurante.model.entity.InventoryItem;
import com.restaurante.model.entity.InventoryMovement;
import com.restaurante.model.entity.InventoryRecipe;
import com.restaurante.model.entity.InventoryRecipeLine;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.ProductInventoryMapping;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.InventoryConsumptionStatus;
import com.restaurante.model.enums.InventoryConsumptionTriggerType;
import com.restaurante.model.enums.InventoryMovementDirection;
import com.restaurante.model.enums.InventoryMovementReferenceType;
import com.restaurante.model.enums.InventoryMovementSource;
import com.restaurante.model.enums.InventoryMovementType;
import com.restaurante.model.enums.InventoryRecipeStatus;
import com.restaurante.model.enums.MarginCalculationBasis;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.ProductStockPolicy;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.restaurante.inventory.util.InventoryMath.scale;

@Service
@RequiredArgsConstructor
public class InventoryConsumptionService {

    private static final String WARN_PRODUCT_WITHOUT_RECIPE = "PRODUCT_WITHOUT_RECIPE";
    private static final String WARN_STOCK_NEGATIVE = "STOCK_NEGATIVE";
    private static final String WARN_STOCK_INSUFFICIENT = "STOCK_INSUFFICIENT";
    private static final String WARN_MARGIN_WITHOUT_FISCAL_DOCUMENT = "MARGIN_WITHOUT_FISCAL_DOCUMENT";

    private final PedidoRepository pedidoRepository;
    private final PagamentoGatewayRepository pagamentoRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final InventoryConsumptionRecordRepository recordRepository;
    private final InventoryConsumptionLineRepository lineRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryItemRepository itemRepository;
    private final ProductInventoryMappingRepository mappingRepository;
    private final InventoryRecipeRepository recipeRepository;
    private final InventoryRecipeLineRepository recipeLineRepository;
    private final UnitConversionService unitConversionService;
    private final TenantInventoryPolicyService tenantInventoryPolicyService;
    private final InventoryProperties properties;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public InventoryConsumptionRecord consumeOnPaymentConfirmed(Long tenantId,
                                                               Long pedidoId,
                                                               Long pagamentoId,
                                                               InventoryMovementSource source) {
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow(() -> new BusinessException("INVENTORY_CONSUMPTION_PEDIDO_NOT_FOUND"));
        if (pedido.getTenant() == null || !pedido.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("INVENTORY_FORBIDDEN");
        }

        Tenant tenant = pedido.getTenant();
        if (!properties.isEnabled()) {
            return ensureSkipped(tenant, pedido, pagamentoId, InventoryConsumptionTriggerType.PAYMENT_CONFIRMED, "inventory-disabled");
        }

        var policy = tenantInventoryPolicyService.getOrCreateDefault(tenant);
        if (!Boolean.TRUE.equals(policy.getStockControlEnabled())) {
            return ensureSkipped(tenant, pedido, pagamentoId, policy.getConsumptionTrigger(), "stock-control-disabled");
        }
        if (policy.getConsumptionTrigger() != InventoryConsumptionTriggerType.PAYMENT_CONFIRMED) {
            return ensureSkipped(tenant, pedido, pagamentoId, policy.getConsumptionTrigger(), "trigger-not-payment-confirmed");
        }

        InventoryConsumptionRecord existing = recordRepository.findByTenantIdAndPedidoId(tenantId, pedidoId).orElse(null);
        if (existing != null && existing.getStatus() == InventoryConsumptionStatus.CONSUMED) {
            return existing;
        }

        if (movementRepository.existsByTenantIdAndReferenceTypeAndReferenceIdAndMovementType(
                tenantId, InventoryMovementReferenceType.PEDIDO, pedidoId, InventoryMovementType.SALE_CONSUMPTION)) {
            if (existing != null) {
                existing.setStatus(InventoryConsumptionStatus.CONSUMED);
                existing.setConsumedAt(LocalDateTime.now());
                return recordRepository.save(existing);
            }
            return ensureSkipped(tenant, pedido, pagamentoId, InventoryConsumptionTriggerType.PAYMENT_CONFIRMED, "already-consumed-by-movements");
        }

        Pagamento pagamento = pagamentoId != null ? pagamentoRepository.findById(pagamentoId).orElse(null) : null;
        if (pagamento != null && pagamento.getTenant() != null && !pagamento.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("INVENTORY_FORBIDDEN");
        }

        InventoryConsumptionRecord record = existing != null ? existing : new InventoryConsumptionRecord();
        record.setTenant(tenant);
        record.setPedido(pedido);
        record.setPagamento(pagamento);
        record.setTriggerType(InventoryConsumptionTriggerType.PAYMENT_CONFIRMED);
        record.setStatus(InventoryConsumptionStatus.PENDING);
        record.setWarningCount(0);
        record = recordRepository.save(record);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.INVENTORY_CONSUMPTION_REQUESTED,
                OperationalEntityType.INVENTORY_CONSUMPTION_RECORD,
                record.getId(),
                OperationalOrigem.SYSTEM,
                "payment-confirmed",
                consumptionMetadata(tenantId, pedidoId, pagamentoId, record.getId(), source, null),
                null,
                null
        );

        try {
            doConsume(record, source != null ? source : InventoryMovementSource.SYSTEM, policy.getMarginCalculationBasis());
            record.setStatus(InventoryConsumptionStatus.CONSUMED);
            record.setConsumedAt(LocalDateTime.now());
            recordRepository.save(record);
            operationalEventLogService.logGenericForTenant(
                    tenantId,
                    OperationalEventType.INVENTORY_CONSUMPTION_COMPLETED,
                    OperationalEntityType.INVENTORY_CONSUMPTION_RECORD,
                    record.getId(),
                    OperationalOrigem.SYSTEM,
                    "consumed",
                    consumptionMetadata(tenantId, pedidoId, pagamentoId, record.getId(), source, null),
                    null,
                    null
            );
            return record;
        } catch (RuntimeException e) {
            record.setStatus(InventoryConsumptionStatus.FAILED);
            record.setWarningCount(record.getWarningCount() != null ? record.getWarningCount() : 0);
            recordRepository.save(record);
            operationalEventLogService.logGenericForTenant(
                    tenantId,
                    OperationalEventType.INVENTORY_CONSUMPTION_FAILED,
                    OperationalEntityType.INVENTORY_CONSUMPTION_RECORD,
                    record.getId(),
                    OperationalOrigem.SYSTEM,
                    "failed",
                    consumptionMetadata(tenantId, pedidoId, pagamentoId, record.getId(), source, e.getClass().getSimpleName()),
                    null,
                    null
            );
            throw e;
        }
    }

    @Transactional
    public InventoryConsumptionRecord reprocess(Long tenantId, Long pedidoId) {
        InventoryConsumptionRecord record = recordRepository.findByTenantIdAndPedidoId(tenantId, pedidoId)
                .orElseThrow(() -> new BusinessException("INVENTORY_CONSUMPTION_NOT_FOUND"));
        if (record.getStatus() == InventoryConsumptionStatus.CONSUMED) return record;
        if (movementRepository.existsByTenantIdAndReferenceTypeAndReferenceIdAndMovementType(
                tenantId, InventoryMovementReferenceType.PEDIDO, pedidoId, InventoryMovementType.SALE_CONSUMPTION)) {
            record.setStatus(InventoryConsumptionStatus.CONSUMED);
            record.setConsumedAt(LocalDateTime.now());
            return recordRepository.save(record);
        }
        return consumeOnPaymentConfirmed(tenantId, pedidoId, record.getPagamento() != null ? record.getPagamento().getId() : null, InventoryMovementSource.ADMIN);
    }

    private InventoryConsumptionRecord ensureSkipped(Tenant tenant,
                                                    Pedido pedido,
                                                    Long pagamentoId,
                                                    InventoryConsumptionTriggerType triggerType,
                                                    String reason) {
        InventoryConsumptionRecord existing = recordRepository.findByTenantIdAndPedidoId(tenant.getId(), pedido.getId()).orElse(null);
        if (existing != null) return existing;
        InventoryConsumptionRecord record = new InventoryConsumptionRecord();
        record.setTenant(tenant);
        record.setPedido(pedido);
        record.setTriggerType(triggerType != null ? triggerType : InventoryConsumptionTriggerType.PAYMENT_CONFIRMED);
        record.setStatus(InventoryConsumptionStatus.SKIPPED);
        record.setWarningCount(0);
        record = recordRepository.save(record);
        operationalEventLogService.logGenericForTenant(
                tenant.getId(),
                OperationalEventType.INVENTORY_CONSUMPTION_SKIPPED,
                OperationalEntityType.INVENTORY_CONSUMPTION_RECORD,
                record.getId(),
                OperationalOrigem.SYSTEM,
                reason,
                consumptionMetadata(tenant.getId(), pedido.getId(), pagamentoId, record.getId(), null, null),
                null,
                null
        );
        return record;
    }

    private Map<String, Object> consumptionMetadata(Long tenantId,
                                                    Long pedidoId,
                                                    Long pagamentoId,
                                                    Long recordId,
                                                    InventoryMovementSource source,
                                                    String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tenantId", tenantId);
        metadata.put("pedidoId", pedidoId);
        metadata.put("pagamentoId", pagamentoId);
        metadata.put("recordId", recordId);
        metadata.put("source", source != null ? source.name() : "SYSTEM");
        if (reason != null) {
            metadata.put("reason", reason);
        }
        return metadata;
    }

    private void doConsume(InventoryConsumptionRecord record,
                           InventoryMovementSource source,
                           MarginCalculationBasis marginCalculationBasis) {
        Pedido pedido = record.getPedido();
        Long tenantId = record.getTenant().getId();

        List<ItemPedido> itensPedido = pedido.getItens() != null ? pedido.getItens() : List.of();
        List<InventoryConsumptionLine> consumptionLines = new ArrayList<>();

        for (ItemPedido itemPedido : itensPedido) {
            Produto product = itemPedido.getProduto();
            if (product == null) continue;

            ProductInventoryMapping mapping = mappingRepository.findByTenantIdAndProductId(tenantId, product.getId()).orElse(null);
            if (mapping == null || mapping.getStockPolicy() == null || mapping.getStockPolicy() == ProductStockPolicy.NO_STOCK_CONTROL || mapping.getStockPolicy() == ProductStockPolicy.MANUAL_ONLY) {
                record.setWarningCount(record.getWarningCount() + 1);
                continue;
            }

            if (mapping.getStockPolicy() == ProductStockPolicy.DIRECT_ITEM_DEDUCTION) {
                if (mapping.getInventoryItem() == null) {
                    record.setWarningCount(record.getWarningCount() + 1);
                    continue;
                }
                InventoryItem invItem = mapping.getInventoryItem();
                BigDecimal qtyBase = scale(BigDecimal.valueOf(itemPedido.getQuantidade()), properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode());
                consumptionLines.add(consumeOne(record, itemPedido, product, invItem, null, qtyBase, invItem.getBaseUnit(), source, null));
                continue;
            }

            InventoryRecipe recipe = mapping.getRecipe();
            if (recipe == null) {
                List<InventoryRecipe> effective = recipeRepository.findEffectiveByProduct(tenantId, product.getId(), InventoryRecipeStatus.ACTIVE, LocalDateTime.now());
                recipe = effective.isEmpty() ? null : effective.get(0);
            }
            if (recipe == null || recipe.getStatus() != InventoryRecipeStatus.ACTIVE) {
                record.setWarningCount(record.getWarningCount() + 1);
                continue;
            }

            List<InventoryRecipeLine> recipeLines = recipeLineRepository.findAllByRecipeIdOrderByIdAsc(recipe.getId());
            for (InventoryRecipeLine recipeLine : recipeLines) {
                InventoryItem ingredient = recipeLine.getInventoryItem();
                if (ingredient == null) continue;
                BigDecimal orderedQty = BigDecimal.valueOf(itemPedido.getQuantidade());
                BigDecimal perUnit = recipeLine.getQuantity().divide(recipe.getYieldQuantity(), properties.getMath().getCalculationScale(), properties.getMath().getRoundingMode());
                BigDecimal consumed = perUnit.multiply(orderedQty);
                if (recipeLine.getWastePercentage() != null && recipeLine.getWastePercentage().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal factor = BigDecimal.ONE.add(recipeLine.getWastePercentage().divide(new BigDecimal("100"), properties.getMath().getCalculationScale(), properties.getMath().getRoundingMode()));
                    consumed = consumed.multiply(factor);
                }

                BigDecimal qtyBase = unitConversionService.convertQuantity(
                        tenantId,
                        consumed,
                        recipeLine.getUnit(),
                        ingredient.getBaseUnit(),
                        properties.getMath().getCalculationScale(),
                        properties.getMath().getRoundingMode()
                );
                qtyBase = scale(qtyBase, properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode());

                consumptionLines.add(consumeOne(record, itemPedido, product, ingredient, recipe, qtyBase, ingredient.getBaseUnit(), source, null));
            }
        }

        BigDecimal totalCost = consumptionLines.stream()
                .map(InventoryConsumptionLine::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        RevenueParts revenueParts = resolveRevenueParts(tenantId, pedido.getId(), record.getPagamento() != null ? record.getPagamento().getId() : null);
        if (revenueParts.missingFiscalDoc) {
            record.setWarningCount(record.getWarningCount() + 1);
        }
        record.setGrossRevenueAmount(revenueParts.gross);
        record.setNetRevenueAmount(revenueParts.net);
        record.setTaxAmount(revenueParts.tax);
        record.setTotalCost(scale(totalCost, properties.getMath().getMonetaryScale(), properties.getMath().getRoundingMode()));

        BigDecimal basis = marginCalculationBasis == MarginCalculationBasis.GROSS_REVENUE ? revenueParts.gross : revenueParts.net;
        if (basis == null) basis = BigDecimal.ZERO;

        BigDecimal margin = basis.subtract(record.getTotalCost() != null ? record.getTotalCost() : BigDecimal.ZERO);
        record.setEstimatedMarginAmount(scale(margin, properties.getMath().getMonetaryScale(), properties.getMath().getRoundingMode()));

        if (basis.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pct = margin.divide(basis, properties.getMath().getCalculationScale(), properties.getMath().getRoundingMode())
                    .multiply(new BigDecimal("100"));
            record.setEstimatedMarginPercentage(scale(pct, 6, properties.getMath().getRoundingMode()));
        } else {
            record.setEstimatedMarginPercentage(null);
        }

        recordRepository.save(record);
        for (InventoryConsumptionLine line : consumptionLines) {
            lineRepository.save(line);
        }
    }

    private InventoryConsumptionLine consumeOne(InventoryConsumptionRecord record,
                                                ItemPedido pedidoItem,
                                                Produto product,
                                                InventoryItem ingredient,
                                                InventoryRecipe recipeOrNull,
                                                BigDecimal quantityBaseUnit,
                                                com.restaurante.model.entity.UnitOfMeasure baseUnit,
                                                InventoryMovementSource source,
                                                String warningCodeOrNull) {
        InventoryItem locked = itemRepository.findByIdForUpdate(ingredient.getId())
                .orElseThrow(() -> new BusinessException("INVENTORY_ITEM_NOT_FOUND"));

        BigDecimal oldQty = locked.getCurrentQuantity() != null ? locked.getCurrentQuantity() : BigDecimal.ZERO;
        BigDecimal newQty = oldQty.subtract(quantityBaseUnit);
        if (!Boolean.TRUE.equals(locked.getAllowNegativeStock()) && newQty.compareTo(BigDecimal.ZERO) < 0) {
            record.setWarningCount(record.getWarningCount() + 1);
            warningCodeOrNull = WARN_STOCK_INSUFFICIENT;
        }
        locked.setCurrentQuantity(scale(newQty, properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode()));
        itemRepository.save(locked);

        BigDecimal unitCost = locked.getAverageCost() != null ? locked.getAverageCost() : BigDecimal.ZERO;
        BigDecimal totalCost = quantityBaseUnit.multiply(unitCost);

        InventoryMovement movement = new InventoryMovement();
        movement.setTenant(record.getTenant());
        movement.setUnidadeAtendimento(record.getPedido().getSessaoConsumo() != null ? record.getPedido().getSessaoConsumo().getUnidadeAtendimento() : null);
        movement.setInventoryItem(locked);
        movement.setMovementType(InventoryMovementType.SALE_CONSUMPTION);
        movement.setDirection(InventoryMovementDirection.OUT);
        movement.setQuantity(quantityBaseUnit);
        movement.setUnit(baseUnit);
        movement.setQuantityBaseUnit(quantityBaseUnit);
        movement.setUnitCost(unitCost);
        movement.setTotalCost(scale(totalCost, properties.getMath().getCalculationScale(), properties.getMath().getRoundingMode()));
        movement.setStockBefore(oldQty);
        movement.setStockAfter(locked.getCurrentQuantity());
        movement.setAverageCostBefore(locked.getAverageCost());
        movement.setAverageCostAfter(locked.getAverageCost());
        movement.setReferenceType(InventoryMovementReferenceType.PEDIDO);
        movement.setReferenceId(record.getPedido().getId());
        movement.setSource(source != null ? source : InventoryMovementSource.SYSTEM);
        movement.setReason("sale-consumption");
        movementRepository.save(movement);

        if (locked.getCurrentQuantity().compareTo(BigDecimal.ZERO) < 0) {
            record.setWarningCount(record.getWarningCount() + 1);
            warningCodeOrNull = WARN_STOCK_NEGATIVE;
        }

        InventoryConsumptionLine line = new InventoryConsumptionLine();
        line.setConsumptionRecord(record);
        line.setTenant(record.getTenant());
        line.setPedidoItem(pedidoItem);
        line.setProduct(product);
        line.setInventoryItem(locked);
        line.setRecipe(recipeOrNull);
        line.setQuantityConsumed(quantityBaseUnit);
        line.setUnit(baseUnit);
        line.setQuantityBaseUnit(quantityBaseUnit);
        line.setUnitCost(unitCost);
        line.setTotalCost(scale(totalCost, properties.getMath().getCalculationScale(), properties.getMath().getRoundingMode()));
        line.setStockBefore(oldQty);
        line.setStockAfter(locked.getCurrentQuantity());
        line.setWarningCode(warningCodeOrNull);
        return line;
    }

    private RevenueParts resolveRevenueParts(Long tenantId, Long pedidoId, Long pagamentoId) {
        BigDecimal gross = BigDecimal.ZERO;
        Pedido pedido = pedidoRepository.findById(pedidoId).orElse(null);
        if (pedido != null && pedido.getTotal() != null) {
            gross = pedido.getTotal();
        }

        if (pagamentoId == null) {
            return new RevenueParts(gross, gross, BigDecimal.ZERO, true);
        }

        FiscalDocument doc = fiscalDocumentRepository.findByTenantIdAndPagamentoId(tenantId, pagamentoId).orElse(null);
        if (doc == null) {
            return new RevenueParts(gross, gross, BigDecimal.ZERO, true);
        }
        BigDecimal net = (doc.getTaxableAmount() != null ? doc.getTaxableAmount() : BigDecimal.ZERO)
                .add(doc.getExemptAmount() != null ? doc.getExemptAmount() : BigDecimal.ZERO);
        BigDecimal tax = doc.getTaxAmount() != null ? doc.getTaxAmount() : BigDecimal.ZERO;
        return new RevenueParts(gross, net, tax, false);
    }

    private record RevenueParts(BigDecimal gross, BigDecimal net, BigDecimal tax, boolean missingFiscalDoc) {}
}
