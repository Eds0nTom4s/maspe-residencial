package com.restaurante.inventory.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.inventory.config.InventoryProperties;
import com.restaurante.inventory.repository.InventoryConsumptionLineRepository;
import com.restaurante.inventory.repository.InventoryConsumptionRecordRepository;
import com.restaurante.inventory.repository.InventoryItemRepository;
import com.restaurante.inventory.repository.InventoryMovementRepository;
import com.restaurante.inventory.repository.InventoryReturnLineRepository;
import com.restaurante.inventory.repository.InventoryReturnRecordRepository;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.InventoryConsumptionLine;
import com.restaurante.model.entity.InventoryConsumptionRecord;
import com.restaurante.model.entity.InventoryItem;
import com.restaurante.model.entity.InventoryMovement;
import com.restaurante.model.entity.InventoryReturnLine;
import com.restaurante.model.entity.InventoryReturnRecord;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantInventoryPolicy;
import com.restaurante.model.entity.UnitOfMeasure;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.InventoryMovementDirection;
import com.restaurante.model.enums.InventoryMovementReferenceType;
import com.restaurante.model.enums.InventoryMovementSource;
import com.restaurante.model.enums.InventoryMovementType;
import com.restaurante.model.enums.InventoryRestockPolicy;
import com.restaurante.model.enums.InventoryReturnReasonCategory;
import com.restaurante.model.enums.InventoryReturnSource;
import com.restaurante.model.enums.InventoryReturnStatus;
import com.restaurante.model.enums.InventoryReturnType;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.restaurante.inventory.util.InventoryMath.scale;

@Service
@RequiredArgsConstructor
public class InventoryReturnService {

    private final PedidoRepository pedidoRepository;
    private final UserRepository userRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final InventoryConsumptionRecordRepository consumptionRecordRepository;
    private final InventoryConsumptionLineRepository consumptionLineRepository;
    private final InventoryReturnRecordRepository returnRecordRepository;
    private final InventoryReturnLineRepository returnLineRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryMovementRepository movementRepository;
    private final TenantInventoryPolicyService tenantInventoryPolicyService;
    private final InventoryProperties properties;
    private final InventoryReturnFinancialCalculator financialCalculator;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public InventoryReturnRecord createReturn(Long tenantId,
                                             Long pedidoId,
                                             InventoryReturnType returnType,
                                             InventoryReturnReasonCategory reasonCategory,
                                             String reasonDescription,
                                             List<RequestedReturnLine> lines,
                                             InventoryReturnSource source,
                                             Long requestedByUserId) {
        if (tenantId == null) throw new BusinessException("INVENTORY_RETURN_FORBIDDEN");
        if (pedidoId == null) throw new BusinessException("INVENTORY_RETURN_ORIGINAL_PEDIDO_NOT_FOUND");
        if (lines == null || lines.isEmpty()) throw new BusinessException("INVENTORY_RETURN_QUANTITY_INVALID");

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new BusinessException("INVENTORY_RETURN_ORIGINAL_PEDIDO_NOT_FOUND"));
        Tenant tenant = pedido.getTenant();
        if (tenant == null || !tenant.getId().equals(tenantId)) {
            throw new BusinessException("INVENTORY_RETURN_FORBIDDEN");
        }

        if (!properties.isEnabled()) {
            throw new BusinessException("INVENTORY_RETURN_POLICY_DISABLED");
        }

        TenantInventoryPolicy policy = tenantInventoryPolicyService.getOrCreateDefault(tenant);
        if (policy.getStatus() == null || !Boolean.TRUE.equals(policy.getAllowReturns())) {
            throw new BusinessException("INVENTORY_RETURN_POLICY_DISABLED");
        }

        InventoryConsumptionRecord consumptionRecord = consumptionRecordRepository.findByTenantIdAndPedidoId(tenantId, pedidoId)
                .orElseThrow(() -> new BusinessException("INVENTORY_RETURN_CONSUMPTION_NOT_FOUND"));

        InventoryReturnRecord record = new InventoryReturnRecord();
        record.setTenant(tenant);
        record.setUnidadeAtendimento(pedido.getSessaoConsumo() != null ? pedido.getSessaoConsumo().getUnidadeAtendimento() : null);
        record.setPedido(pedido);
        record.setPagamento(consumptionRecord.getPagamento());
        record.setConsumptionRecord(consumptionRecord);
        record.setReturnType(returnType != null ? returnType : InventoryReturnType.PARTIAL_ORDER_RETURN);
        record.setSource(source != null ? source : InventoryReturnSource.ADMIN);
        record.setReasonCategory(reasonCategory);
        record.setReasonDescription(safeText(reasonDescription, 1000));

        User requestedBy = requestedByUserId != null ? userRepository.findById(requestedByUserId).orElse(null) : null;
        record.setRequestedBy(requestedBy);
        record.setRequestedAt(LocalDateTime.now());

        if (consumptionRecord.getPagamento() != null) {
            FiscalDocument fd = fiscalDocumentRepository
                    .findByTenantIdAndPedidoIdAndPagamentoId(tenantId, pedidoId, consumptionRecord.getPagamento().getId())
                    .orElse(null);
            record.setFiscalDocument(fd);
        }

        InventoryReturnStatus initialStatus = Boolean.TRUE.equals(policy.getRequireReturnApproval())
                ? InventoryReturnStatus.SUBMITTED
                : InventoryReturnStatus.APPROVED;
        record.setStatus(initialStatus);
        if (initialStatus == InventoryReturnStatus.APPROVED) {
            record.setApprovedBy(requestedBy);
            record.setApprovedAt(LocalDateTime.now());
        }

        record = returnRecordRepository.save(record);

        List<InventoryReturnLine> createdLines = new ArrayList<>();
        for (RequestedReturnLine req : lines) {
            if (req == null) continue;
            if (req.pedidoItemId() == null) throw new BusinessException("INVENTORY_RETURN_LINE_NOT_FOUND");
            if (req.quantityReturned() == null || req.quantityReturned().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("INVENTORY_RETURN_QUANTITY_INVALID");
            }

            ItemPedido pedidoItem = findPedidoItem(pedido, req.pedidoItemId());
            if (pedidoItem == null) throw new BusinessException("INVENTORY_RETURN_LINE_NOT_FOUND");

            int itemQtyInt = pedidoItem.getQuantidade();
            if (itemQtyInt <= 0) throw new BusinessException("INVENTORY_RETURN_QUANTITY_INVALID");
            BigDecimal itemQty = BigDecimal.valueOf(itemQtyInt);
            if (!Boolean.TRUE.equals(policy.getAllowPartialReturns()) && req.quantityReturned().compareTo(itemQty) != 0) {
                throw new BusinessException("INVENTORY_RETURN_QUANTITY_INVALID");
            }
            if (req.quantityReturned().compareTo(itemQty) > 0) {
                throw new BusinessException("INVENTORY_RETURN_EXCEEDS_CONSUMED_QUANTITY");
            }

            BigDecimal factor = req.quantityReturned()
                    .divide(itemQty, properties.getMath().getCalculationScale(), properties.getMath().getRoundingMode());

            List<InventoryConsumptionLine> consumptionLines = consumptionLineRepository
                    .findAllByConsumptionRecordIdAndPedidoItemIdOrderByIdAsc(consumptionRecord.getId(), pedidoItem.getId());
            if (consumptionLines.isEmpty()) {
                throw new BusinessException("INVENTORY_RETURN_CONSUMPTION_NOT_FOUND");
            }

            InventoryRestockPolicy restockPolicy = req.restockPolicy() != null ? req.restockPolicy() : policy.getDefaultRestockPolicy();
            if (restockPolicy == null) throw new BusinessException("INVENTORY_RETURN_RESTOCK_POLICY_REQUIRED");

            for (InventoryConsumptionLine cl : consumptionLines) {
                BigDecimal alreadyReturnedBase = returnLineRepository.sumProcessedReturnedBaseQtyByConsumptionLine(tenantId, cl.getId());
                BigDecimal remainingBase = nz(cl.getQuantityBaseUnit()).subtract(nz(alreadyReturnedBase));

                BigDecimal baseToReturn = nz(cl.getQuantityBaseUnit()).multiply(factor);
                baseToReturn = scale(baseToReturn, properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode());

                if (baseToReturn.compareTo(remainingBase) > 0) {
                    throw new BusinessException("INVENTORY_RETURN_EXCEEDS_CONSUMED_QUANTITY");
                }

                InventoryReturnLine rl = new InventoryReturnLine();
                rl.setReturnRecord(record);
                rl.setTenant(tenant);
                rl.setPedidoItem(cl.getPedidoItem());
                rl.setProduct(cl.getProduct());
                rl.setConsumptionLine(cl);
                rl.setInventoryItem(cl.getInventoryItem());
                rl.setRecipe(cl.getRecipe());

                BigDecimal qtyReturned = nz(cl.getQuantityConsumed()).multiply(factor);
                qtyReturned = scale(qtyReturned, properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode());
                rl.setQuantityReturned(qtyReturned);
                rl.setUnit(cl.getUnit());
                rl.setQuantityBaseUnit(baseToReturn);
                rl.setUnitCost(nz(cl.getUnitCost()));
                rl.setTotalCostReversed(scale(nz(cl.getTotalCost()).multiply(factor), properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode()));
                rl.setRestockPolicy(restockPolicy);
                createdLines.add(returnLineRepository.save(rl));
            }
        }

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.INVENTORY_RETURN_CREATED,
                OperationalEntityType.INVENTORY_RETURN_RECORD,
                record.getId(),
                OperationalOrigem.SYSTEM,
                "Return criado",
                Map.of(
                        "tenantId", tenantId,
                        "pedidoId", pedidoId,
                        "returnId", record.getId(),
                        "status", record.getStatus().name(),
                        "returnType", record.getReturnType().name(),
                        "source", record.getSource().name(),
                        "linesCount", createdLines.size()
                ),
                null,
                null
        );

        if (record.getStatus() == InventoryReturnStatus.APPROVED) {
            return processReturn(tenantId, record.getId(), requestedByUserId);
        }

        return record;
    }

    @Transactional
    public InventoryReturnRecord submit(Long tenantId, Long returnId, Long requestedByUserId) {
        InventoryReturnRecord record = locked(tenantId, returnId);
        if (record.getStatus() != InventoryReturnStatus.DRAFT) {
            throw new BusinessException("INVENTORY_RETURN_INVALID_STATE");
        }
        record.setStatus(InventoryReturnStatus.SUBMITTED);
        record.setRequestedBy(requestedByUserId != null ? userRepository.findById(requestedByUserId).orElse(null) : null);
        record.setRequestedAt(LocalDateTime.now());
        record = returnRecordRepository.save(record);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.INVENTORY_RETURN_SUBMITTED,
                OperationalEntityType.INVENTORY_RETURN_RECORD,
                record.getId(),
                OperationalOrigem.SYSTEM,
                "Return submetido",
                Map.of("tenantId", tenantId, "returnId", record.getId(), "pedidoId", record.getPedido().getId()),
                null,
                null
        );
        return record;
    }

    @Transactional
    public InventoryReturnRecord approve(Long tenantId, Long returnId, Long approvedByUserId) {
        InventoryReturnRecord record = locked(tenantId, returnId);
        if (record.getStatus() != InventoryReturnStatus.SUBMITTED) {
            throw new BusinessException("INVENTORY_RETURN_INVALID_STATE");
        }
        record.setStatus(InventoryReturnStatus.APPROVED);
        record.setApprovedBy(approvedByUserId != null ? userRepository.findById(approvedByUserId).orElse(null) : null);
        record.setApprovedAt(LocalDateTime.now());
        record = returnRecordRepository.save(record);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.INVENTORY_RETURN_APPROVED,
                OperationalEntityType.INVENTORY_RETURN_RECORD,
                record.getId(),
                OperationalOrigem.SYSTEM,
                "Return aprovado",
                Map.of("tenantId", tenantId, "returnId", record.getId(), "pedidoId", record.getPedido().getId()),
                null,
                null
        );
        return record;
    }

    @Transactional
    public InventoryReturnRecord reject(Long tenantId, Long returnId, String reason, Long reviewedByUserId) {
        InventoryReturnRecord record = locked(tenantId, returnId);
        if (record.getStatus() != InventoryReturnStatus.SUBMITTED) {
            throw new BusinessException("INVENTORY_RETURN_INVALID_STATE");
        }
        record.setStatus(InventoryReturnStatus.REJECTED);
        record.setReasonDescription(safeText(reason != null ? reason : record.getReasonDescription(), 1000));
        record = returnRecordRepository.save(record);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.INVENTORY_RETURN_REJECTED,
                OperationalEntityType.INVENTORY_RETURN_RECORD,
                record.getId(),
                OperationalOrigem.SYSTEM,
                "Return rejeitado",
                Map.of("tenantId", tenantId, "returnId", record.getId(), "pedidoId", record.getPedido().getId()),
                null,
                null
        );
        return record;
    }

    @Transactional
    public InventoryReturnRecord processReturn(Long tenantId, Long returnId, Long processedByUserId) {
        InventoryReturnRecord record = locked(tenantId, returnId);
        if (record.getStatus() == InventoryReturnStatus.PROCESSED) return record;
        if (record.getStatus() != InventoryReturnStatus.APPROVED) {
            throw new BusinessException("INVENTORY_RETURN_INVALID_STATE");
        }

        List<InventoryReturnLine> lines = returnLineRepository.findAllByReturnRecordIdOrderByIdAsc(record.getId());
        if (lines.isEmpty()) {
            throw new BusinessException("INVENTORY_RETURN_PROCESSING_FAILED");
        }

        TenantInventoryPolicy policy = tenantInventoryPolicyService.getOrCreateDefault(record.getTenant());
        if (policy != null && Boolean.TRUE.equals(policy.getBlockProcessWhenManualReviewLineExists())) {
            for (InventoryReturnLine rl : lines) {
                if (rl != null && rl.getRestockPolicy() == InventoryRestockPolicy.MANUAL_REVIEW) {
                    operationalEventLogService.logGenericForTenant(
                            tenantId,
                            OperationalEventType.INVENTORY_RETURN_PROCESSING_BLOCKED_MANUAL_REVIEW,
                            OperationalEntityType.INVENTORY_RETURN_RECORD,
                            record.getId(),
                            OperationalOrigem.SYSTEM,
                            "Processamento bloqueado por linha MANUAL_REVIEW",
                            Map.of("tenantId", tenantId, "returnId", record.getId()),
                            null,
                            null
                    );
                    throw new BusinessException("INVENTORY_RETURN_PROCESSING_BLOCKED_MANUAL_REVIEW");
                }
            }
        }

        BigDecimal totalCost = BigDecimal.ZERO;
        int warnings = 0;
        boolean hasWaste = false;
        boolean hasNoRestock = false;

        var totals = financialCalculator.calculate(record, lines);
        if (totals.estimated()) {
            warnings++;
        }

        for (InventoryReturnLine rl : lines) {
            if (rl.getMovement() != null || rl.getWasteMovement() != null || rl.getCogsReversalMovement() != null) continue;

            if (rl.getRestockPolicy() == InventoryRestockPolicy.RESTOCK) {
                InventoryItem item = itemRepository.findByIdForUpdate(rl.getInventoryItem().getId())
                        .orElseThrow(() -> new BusinessException("INVENTORY_ITEM_NOT_FOUND"));

                BigDecimal stockBefore = nz(item.getCurrentQuantity());
                BigDecimal stockAfter = stockBefore.add(nz(rl.getQuantityBaseUnit()));
                stockAfter = scale(stockAfter, properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode());
                item.setCurrentQuantity(stockAfter);
                itemRepository.save(item);

                InventoryMovement m = new InventoryMovement();
                m.setTenant(record.getTenant());
                m.setUnidadeAtendimento(record.getUnidadeAtendimento());
                m.setInventoryItem(item);
                m.setMovementType(InventoryMovementType.RETURN_IN);
                m.setDirection(InventoryMovementDirection.IN);
                m.setQuantity(nz(rl.getQuantityReturned()));
                m.setUnit(rl.getUnit() != null ? rl.getUnit() : requireUnit(item));
                m.setQuantityBaseUnit(nz(rl.getQuantityBaseUnit()));
                m.setUnitCost(nz(rl.getUnitCost()));
                m.setTotalCost(scale(nz(rl.getTotalCostReversed()), properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode()));
                m.setStockBefore(stockBefore);
                m.setStockAfter(stockAfter);
                m.setAverageCostBefore(item.getAverageCost());
                m.setAverageCostAfter(item.getAverageCost());
                m.setReferenceType(InventoryMovementReferenceType.INVENTORY_RETURN);
                m.setReferenceId(record.getId());
                m.setSource(mapMovementSource(record.getSource()));
                m.setReason(safeText(record.getReasonDescription(), 500));
                m.setCreatedByUser(processedByUserId != null ? userRepository.findById(processedByUserId).orElse(null) : null);
                m = movementRepository.save(m);

                rl.setMovement(m);
                rl.setStockBefore(stockBefore);
                rl.setStockAfter(stockAfter);
                returnLineRepository.save(rl);
            } else if (rl.getRestockPolicy() == InventoryRestockPolicy.WASTE) {
                hasWaste = true;
                InventoryItem item = itemRepository.findByIdForUpdate(rl.getInventoryItem().getId())
                        .orElseThrow(() -> new BusinessException("INVENTORY_ITEM_NOT_FOUND"));
                BigDecimal stockBefore = nz(item.getCurrentQuantity());
                BigDecimal stockAfter = stockBefore;

                InventoryMovement wasteMv = new InventoryMovement();
                wasteMv.setTenant(record.getTenant());
                wasteMv.setUnidadeAtendimento(record.getUnidadeAtendimento());
                wasteMv.setInventoryItem(item);
                wasteMv.setMovementType(InventoryMovementType.RETURN_WASTE);
                wasteMv.setDirection(InventoryMovementDirection.OUT);
                wasteMv.setQuantity(nz(rl.getQuantityReturned()));
                wasteMv.setUnit(rl.getUnit() != null ? rl.getUnit() : requireUnit(item));
                wasteMv.setQuantityBaseUnit(nz(rl.getQuantityBaseUnit()));
                wasteMv.setUnitCost(nz(rl.getUnitCost()));
                wasteMv.setTotalCost(scale(nz(rl.getTotalCostReversed()), properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode()));
                wasteMv.setStockBefore(stockBefore);
                wasteMv.setStockAfter(stockAfter);
                wasteMv.setAverageCostBefore(item.getAverageCost());
                wasteMv.setAverageCostAfter(item.getAverageCost());
                wasteMv.setReferenceType(InventoryMovementReferenceType.INVENTORY_RETURN);
                wasteMv.setReferenceId(record.getId());
                wasteMv.setSource(mapMovementSource(record.getSource()));
                wasteMv.setReason(safeText(record.getReasonDescription(), 500));
                wasteMv.setCreatedByUser(processedByUserId != null ? userRepository.findById(processedByUserId).orElse(null) : null);
                wasteMv = movementRepository.save(wasteMv);

                rl.setWasteMovement(wasteMv);
                rl.setStockBefore(stockBefore);
                rl.setStockAfter(stockAfter);
                rl.setWarningCode("RETURN_WASTE_NOT_RESTOCKED");
                warnings++;
                returnLineRepository.save(rl);
                operationalEventLogService.logGenericForTenant(
                        tenantId,
                        OperationalEventType.INVENTORY_RETURN_WASTE_RECORDED,
                        OperationalEntityType.INVENTORY_RETURN_LINE,
                        rl.getId(),
                        OperationalOrigem.SYSTEM,
                        "Return line marcada como WASTE (sem restock)",
                        Map.of("tenantId", tenantId, "returnId", record.getId(), "lineId", rl.getId(), "movementId", wasteMv.getId()),
                        null,
                        null
                );
            } else if (rl.getRestockPolicy() == InventoryRestockPolicy.DO_NOT_RESTOCK) {
                hasNoRestock = true;
                InventoryItem item = itemRepository.findByIdForUpdate(rl.getInventoryItem().getId())
                        .orElseThrow(() -> new BusinessException("INVENTORY_ITEM_NOT_FOUND"));
                BigDecimal stockBefore = nz(item.getCurrentQuantity());
                BigDecimal stockAfter = stockBefore;

                InventoryMovement cogsOnly = new InventoryMovement();
                cogsOnly.setTenant(record.getTenant());
                cogsOnly.setUnidadeAtendimento(record.getUnidadeAtendimento());
                cogsOnly.setInventoryItem(item);
                cogsOnly.setMovementType(InventoryMovementType.COGS_REVERSAL_ONLY);
                cogsOnly.setDirection(InventoryMovementDirection.OUT);
                cogsOnly.setQuantity(nz(rl.getQuantityReturned()));
                cogsOnly.setUnit(rl.getUnit() != null ? rl.getUnit() : requireUnit(item));
                cogsOnly.setQuantityBaseUnit(nz(rl.getQuantityBaseUnit()));
                cogsOnly.setUnitCost(nz(rl.getUnitCost()));
                cogsOnly.setTotalCost(scale(nz(rl.getTotalCostReversed()), properties.getMath().getQuantityScale(), properties.getMath().getRoundingMode()));
                cogsOnly.setStockBefore(stockBefore);
                cogsOnly.setStockAfter(stockAfter);
                cogsOnly.setAverageCostBefore(item.getAverageCost());
                cogsOnly.setAverageCostAfter(item.getAverageCost());
                cogsOnly.setReferenceType(InventoryMovementReferenceType.INVENTORY_RETURN);
                cogsOnly.setReferenceId(record.getId());
                cogsOnly.setSource(mapMovementSource(record.getSource()));
                cogsOnly.setReason(safeText(record.getReasonDescription(), 500));
                cogsOnly.setCreatedByUser(processedByUserId != null ? userRepository.findById(processedByUserId).orElse(null) : null);
                cogsOnly = movementRepository.save(cogsOnly);

                rl.setCogsReversalMovement(cogsOnly);
                rl.setStockBefore(stockBefore);
                rl.setStockAfter(stockAfter);
                rl.setWarningCode("RETURN_DOES_NOT_RESTOCK");
                warnings++;
                returnLineRepository.save(rl);
                operationalEventLogService.logGenericForTenant(
                        tenantId,
                        OperationalEventType.INVENTORY_RETURN_NO_RESTOCK_RECORDED,
                        OperationalEntityType.INVENTORY_RETURN_LINE,
                        rl.getId(),
                        OperationalOrigem.SYSTEM,
                        "Return line DO_NOT_RESTOCK (sem restock)",
                        Map.of("tenantId", tenantId, "returnId", record.getId(), "lineId", rl.getId(), "movementId", cogsOnly.getId()),
                        null,
                        null
                );
            } else {
                warnings++;
                rl.setWarningCode("RETURN_MANUAL_REVIEW_REQUIRED");
                returnLineRepository.save(rl);
            }

            totalCost = totalCost.add(scale(nz(rl.getTotalCostReversed()), properties.getMath().getMonetaryScale(), properties.getMath().getRoundingMode()));
        }

        BigDecimal revenueReversed = totals.revenueReversed();
        BigDecimal taxReversed = totals.taxReversed();
        financialCalculator.applyToLines(record, lines, revenueReversed, taxReversed);
        for (InventoryReturnLine rl : lines) {
            returnLineRepository.save(rl);
        }

        BigDecimal marginReversed = nz(revenueReversed).subtract(scale(totalCost, properties.getMath().getMonetaryScale(), properties.getMath().getRoundingMode()));

        record.setTotalReturnCost(scale(totalCost, properties.getMath().getMonetaryScale(), properties.getMath().getRoundingMode()));
        record.setTotalRevenueReversed(scale(revenueReversed, properties.getMath().getMonetaryScale(), properties.getMath().getRoundingMode()));
        record.setTotalTaxReversed(scale(taxReversed, properties.getMath().getMonetaryScale(), properties.getMath().getRoundingMode()));
        record.setTotalMarginReversed(scale(marginReversed, properties.getMath().getMonetaryScale(), properties.getMath().getRoundingMode()));
        record.setWarningCount(warnings);
        record.setProcessedAt(LocalDateTime.now());
        record.setStatus(InventoryReturnStatus.PROCESSED);
        record = returnRecordRepository.save(record);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.INVENTORY_RETURN_FINANCIALS_CALCULATED,
                OperationalEntityType.INVENTORY_RETURN_RECORD,
                record.getId(),
                OperationalOrigem.SYSTEM,
                "Financial reversal calculado para return",
                Map.of(
                        "tenantId", tenantId,
                        "returnId", record.getId(),
                        "pedidoId", record.getPedido().getId(),
                        "totalRevenueReversed", record.getTotalRevenueReversed(),
                        "totalTaxReversed", record.getTotalTaxReversed(),
                        "totalMarginReversed", record.getTotalMarginReversed(),
                        "estimated", totals.estimated()
                ),
                null,
                null
        );

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.INVENTORY_RETURN_PROCESSED,
                OperationalEntityType.INVENTORY_RETURN_RECORD,
                record.getId(),
                OperationalOrigem.SYSTEM,
                "Return processado",
                Map.of(
                        "tenantId", tenantId,
                        "returnId", record.getId(),
                        "pedidoId", record.getPedido().getId(),
                        "totalReturnCost", record.getTotalReturnCost(),
                        "warningCount", record.getWarningCount()
                ),
                null,
                null
        );

        return record;
    }

    @Transactional(readOnly = true)
    public InventoryReturnRecord getByTenant(Long tenantId, Long returnId) {
        return returnRecordRepository.findByTenantIdAndId(tenantId, returnId)
                .orElseThrow(() -> new BusinessException("INVENTORY_RETURN_NOT_FOUND"));
    }

    @Transactional
    public InventoryReturnRecord attachRefund(Long tenantId, Long returnId, String refundReferenceId, String refundEventId) {
        InventoryReturnRecord record = locked(tenantId, returnId);
        record.setRefundReferenceId(safeText(refundReferenceId, 120));
        record.setRefundEventId(safeText(refundEventId, 120));
        return returnRecordRepository.save(record);
    }

    @Transactional(readOnly = true)
    public List<InventoryReturnLine> listLines(Long tenantId, Long returnId) {
        InventoryReturnRecord record = getByTenant(tenantId, returnId);
        if (record.getTenant() == null || !record.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("INVENTORY_RETURN_FORBIDDEN");
        }
        return returnLineRepository.findAllByReturnRecordIdOrderByIdAsc(returnId);
    }

    private InventoryReturnRecord locked(Long tenantId, Long returnId) {
        InventoryReturnRecord record = returnRecordRepository.findByIdForUpdate(returnId)
                .orElseThrow(() -> new BusinessException("INVENTORY_RETURN_NOT_FOUND"));
        if (record.getTenant() == null || !record.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("INVENTORY_RETURN_FORBIDDEN");
        }
        return record;
    }

    private ItemPedido findPedidoItem(Pedido pedido, Long pedidoItemId) {
        if (pedido == null || pedido.getItens() == null) return null;
        for (ItemPedido it : pedido.getItens()) {
            if (it != null && it.getId() != null && it.getId().equals(pedidoItemId)) return it;
        }
        return null;
    }

    private UnitOfMeasure requireUnit(InventoryItem item) {
        UnitOfMeasure u = item != null ? item.getBaseUnit() : null;
        if (u == null) throw new BusinessException("INVENTORY_UNIT_NOT_FOUND");
        return u;
    }

    private InventoryMovementSource mapMovementSource(InventoryReturnSource source) {
        if (source == null) return InventoryMovementSource.SYSTEM;
        return switch (source) {
            case POS -> InventoryMovementSource.POS;
            case TENANT_ADMIN -> InventoryMovementSource.ADMIN;
            case SYSTEM_REFUND_EVENT -> InventoryMovementSource.SYSTEM;
            case FISCAL_CORRECTION -> InventoryMovementSource.SYSTEM;
            case ADMIN -> InventoryMovementSource.ADMIN;
        };
    }

    private String safeText(String input, int max) {
        if (input == null) return null;
        String v = input.trim();
        if (v.isEmpty()) return null;
        if (v.length() <= max) return v;
        return v.substring(0, max);
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    public record RequestedReturnLine(Long pedidoItemId, BigDecimal quantityReturned, InventoryRestockPolicy restockPolicy) {
    }
}
