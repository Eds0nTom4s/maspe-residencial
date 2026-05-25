package com.restaurante.controller;

import com.restaurante.dto.request.*;
import com.restaurante.dto.response.*;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.inventory.repository.InventoryConsumptionLineRepository;
import com.restaurante.inventory.repository.InventoryConsumptionRecordRepository;
import com.restaurante.inventory.repository.InventoryMovementRepository;
import com.restaurante.inventory.repository.TenantInventoryPolicyRepository;
import com.restaurante.inventory.service.InventoryConsumptionService;
import com.restaurante.inventory.service.InventoryItemService;
import com.restaurante.inventory.service.InventoryRecipeService;
import com.restaurante.inventory.service.InventoryStockService;
import com.restaurante.inventory.service.InventoryUnitService;
import com.restaurante.inventory.service.ProductInventoryMappingService;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.InventoryItemStatus;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.repository.TenantRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tenant/inventory")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Tenant Admin - Inventory", description = "Inventário operacional: itens, stock, receitas, consumo e COGS/margem")
public class TenantInventoryController {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final TenantInventoryPolicyRepository policyRepository;
    private final InventoryItemService itemService;
    private final InventoryStockService stockService;
    private final InventoryRecipeService recipeService;
    private final ProductInventoryMappingService mappingService;
    private final InventoryUnitService unitService;
    private final InventoryMovementRepository movementRepository;
    private final InventoryConsumptionService consumptionService;
    private final InventoryConsumptionRecordRepository consumptionRecordRepository;
    private final InventoryConsumptionLineRepository consumptionLineRepository;

    @GetMapping("/policy")
    public ResponseEntity<ApiResponse<TenantInventoryPolicyResponse>> getPolicy() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        TenantInventoryPolicy p = policyRepository.findByTenantId(ctx.tenantId()).orElse(null);
        return ResponseEntity.ok(ApiResponse.success("Policy", p != null ? map(p) : null));
    }

    @PutMapping("/policy")
    public ResponseEntity<ApiResponse<TenantInventoryPolicyResponse>> upsertPolicy(@Valid @RequestBody UpsertTenantInventoryPolicyRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new BusinessException("Tenant não encontrado: " + ctx.tenantId()));
        TenantInventoryPolicy p = policyRepository.findByTenantId(ctx.tenantId()).orElseGet(() -> {
            TenantInventoryPolicy created = new TenantInventoryPolicy();
            created.setTenant(tenant);
            return created;
        });
        if (request.getStockControlEnabled() != null) p.setStockControlEnabled(request.getStockControlEnabled());
        if (request.getAllowNegativeStockDefault() != null) p.setAllowNegativeStockDefault(request.getAllowNegativeStockDefault());
        if (request.getStrictStockRequiredForSale() != null) p.setStrictStockRequiredForSale(request.getStrictStockRequiredForSale());
        if (request.getConsumptionTrigger() != null) p.setConsumptionTrigger(request.getConsumptionTrigger());
        if (request.getRequireRecipeForStockedProducts() != null) p.setRequireRecipeForStockedProducts(request.getRequireRecipeForStockedProducts());
        if (request.getUseAverageCost() != null) p.setUseAverageCost(request.getUseAverageCost());
        if (request.getMarginCalculationBasis() != null) p.setMarginCalculationBasis(request.getMarginCalculationBasis());
        if (request.getStatus() != null) p.setStatus(request.getStatus());
        p = policyRepository.save(p);
        return ResponseEntity.ok(ApiResponse.success("Policy", map(p)));
    }

    @GetMapping("/items")
    public ResponseEntity<ApiResponse<Page<InventoryItemResponse>>> listItems(@RequestParam(name = "status", required = false) InventoryItemStatus status,
                                                                              Pageable pageable) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        Page<InventoryItem> page = itemService.list(ctx.tenantId(), status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Items", page.map(this::map)));
    }

    @GetMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> getItem(@PathVariable Long itemId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        return ResponseEntity.ok(ApiResponse.success("Item", map(itemService.get(ctx.tenantId(), itemId))));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> createItem(@Valid @RequestBody CreateInventoryItemRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new BusinessException("Tenant não encontrado: " + ctx.tenantId()));
        InventoryItem item = itemService.create(
                tenant,
                request.getName(),
                request.getSku(),
                request.getType(),
                request.getCategory(),
                request.getBaseUnitCode(),
                request.getStockControlEnabled(),
                request.getAllowNegativeStock(),
                request.getMinimumQuantity(),
                request.getReorderQuantity()
        );
        return ResponseEntity.ok(ApiResponse.success("Item criado", map(item)));
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> updateItem(@PathVariable Long itemId, @Valid @RequestBody UpdateInventoryItemRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        InventoryItem item = itemService.update(
                ctx.tenantId(),
                itemId,
                request.getName(),
                request.getSku(),
                request.getType(),
                request.getCategory(),
                request.getBaseUnitCode(),
                request.getStockControlEnabled(),
                request.getAllowNegativeStock(),
                request.getMinimumQuantity(),
                request.getReorderQuantity(),
                request.getStatus()
        );
        return ResponseEntity.ok(ApiResponse.success("Item atualizado", map(item)));
    }

    @PostMapping("/items/{itemId}/stock-in")
    public ResponseEntity<ApiResponse<InventoryMovementResponse>> stockIn(@PathVariable Long itemId,
                                                                          @Valid @RequestBody InventoryStockInRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        InventoryMovement mv = stockService.stockIn(ctx.tenantId(), itemId, request.getQuantity(), request.getUnit(), request.getUnitCost(), request.getReason(), request.getReference());
        return ResponseEntity.ok(ApiResponse.success("Stock-in criado", map(mv)));
    }

    @PostMapping("/items/{itemId}/adjust")
    public ResponseEntity<ApiResponse<InventoryMovementResponse>> adjust(@PathVariable Long itemId,
                                                                         @Valid @RequestBody InventoryAdjustRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        InventoryMovement mv = stockService.adjust(ctx.tenantId(), itemId, request.getDirection(), request.getQuantity(), request.getUnit(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Ajuste criado", map(mv)));
    }

    @PostMapping("/items/{itemId}/waste")
    public ResponseEntity<ApiResponse<InventoryMovementResponse>> waste(@PathVariable Long itemId,
                                                                        @Valid @RequestBody InventoryWasteRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        InventoryMovement mv = stockService.waste(ctx.tenantId(), itemId, request.getQuantity(), request.getUnit(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Perda registrada", map(mv)));
    }

    @GetMapping("/units")
    public ResponseEntity<ApiResponse<List<UnitOfMeasureResponse>>> listUnits() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        List<UnitOfMeasure> units = unitService.listUnits(ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.success("Units", units.stream().map(this::map).toList()));
    }

    @PostMapping("/units")
    public ResponseEntity<ApiResponse<UnitOfMeasureResponse>> createUnit(@Valid @RequestBody CreateUnitOfMeasureRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new BusinessException("Tenant não encontrado: " + ctx.tenantId()));
        UnitOfMeasure u = unitService.createUnit(tenant, request.getCode(), request.getName(), request.getType(), request.isDecimalAllowed());
        return ResponseEntity.ok(ApiResponse.success("Unit criada", map(u)));
    }

    @PostMapping("/unit-conversions")
    public ResponseEntity<ApiResponse<UnitConversionResponse>> createConversion(@Valid @RequestBody CreateUnitConversionRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        UnitConversion c = unitService.createConversion(ctx.tenantId(), request.getFromUnitId(), request.getToUnitId(), request.getFactor());
        return ResponseEntity.ok(ApiResponse.success("Conversão criada", map(c)));
    }

    @PostMapping("/recipes")
    public ResponseEntity<ApiResponse<InventoryRecipeResponse>> createRecipe(@Valid @RequestBody CreateInventoryRecipeRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new BusinessException("Tenant não encontrado: " + ctx.tenantId()));
        InventoryRecipe recipe = recipeService.createRecipe(tenant, request.getProductId(), request.getName(), request.getYieldQuantity(), request.getYieldUnit());
        return ResponseEntity.ok(ApiResponse.success("Recipe criada", map(recipe)));
    }

    @PostMapping("/recipes/{recipeId}/lines")
    public ResponseEntity<ApiResponse<InventoryRecipeLineResponse>> addRecipeLine(@PathVariable Long recipeId,
                                                                                  @Valid @RequestBody AddInventoryRecipeLineRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new BusinessException("Tenant não encontrado: " + ctx.tenantId()));
        InventoryRecipeLine line = recipeService.addLine(tenant, recipeId, request.getInventoryItemId(), request.getQuantity(), request.getUnit(), request.getWastePercentage());
        return ResponseEntity.ok(ApiResponse.success("Linha criada", map(line)));
    }

    @GetMapping("/recipes/{recipeId}/lines")
    public ResponseEntity<ApiResponse<List<InventoryRecipeLineResponse>>> listRecipeLines(@PathVariable Long recipeId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        List<InventoryRecipeLine> lines = recipeService.listLines(ctx.tenantId(), recipeId);
        return ResponseEntity.ok(ApiResponse.success("Linhas", lines.stream().map(this::map).toList()));
    }

    @PostMapping("/recipes/{recipeId}/activate")
    public ResponseEntity<ApiResponse<InventoryRecipeResponse>> activateRecipe(@PathVariable Long recipeId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        InventoryRecipe recipe = recipeService.activate(ctx.tenantId(), recipeId);
        return ResponseEntity.ok(ApiResponse.success("Recipe ativada", map(recipe)));
    }

    @PostMapping("/recipes/{recipeId}/archive")
    public ResponseEntity<ApiResponse<InventoryRecipeResponse>> archiveRecipe(@PathVariable Long recipeId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        InventoryRecipe recipe = recipeService.archive(ctx.tenantId(), recipeId);
        return ResponseEntity.ok(ApiResponse.success("Recipe arquivada", map(recipe)));
    }

    @GetMapping("/product-mappings")
    public ResponseEntity<ApiResponse<List<ProductInventoryMappingResponse>>> listMappings() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        return ResponseEntity.ok(ApiResponse.success("Mappings", mappingService.list(ctx.tenantId()).stream().map(this::map).toList()));
    }

    @PostMapping("/product-mappings")
    public ResponseEntity<ApiResponse<ProductInventoryMappingResponse>> upsertMapping(@Valid @RequestBody UpsertProductInventoryMappingRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new BusinessException("Tenant não encontrado: " + ctx.tenantId()));
        ProductInventoryMapping m = mappingService.upsert(tenant, request.getProductId(), request.getInventoryItemId(), request.getRecipeId(), request.getStockPolicy());
        return ResponseEntity.ok(ApiResponse.success("Mapping", map(m)));
    }

    @GetMapping("/movements")
    public ResponseEntity<ApiResponse<Page<InventoryMovementResponse>>> listMovements(@RequestParam(name = "itemId", required = false) Long itemId,
                                                                                      Pageable pageable) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        Page<InventoryMovement> page = movementRepository.listByTenant(ctx.tenantId(), itemId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Movements", page.map(this::map)));
    }

    @GetMapping("/consumptions")
    public ResponseEntity<ApiResponse<Page<InventoryConsumptionRecordResponse>>> listConsumptions(Pageable pageable) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        return ResponseEntity.ok(ApiResponse.success("Consumptions", consumptionRecordRepository.listByTenant(ctx.tenantId(), pageable).map(this::map)));
    }

    @GetMapping("/consumptions/{consumptionId}")
    public ResponseEntity<ApiResponse<InventoryConsumptionDetailsResponse>> getConsumption(@PathVariable Long consumptionId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        InventoryConsumptionRecord record = consumptionRecordRepository.findById(consumptionId).orElseThrow(() -> new BusinessException("INVENTORY_CONSUMPTION_NOT_FOUND"));
        if (record.getTenant() == null || !record.getTenant().getId().equals(ctx.tenantId())) throw new BusinessException("INVENTORY_FORBIDDEN");
        var lines = consumptionLineRepository.findAllByConsumptionRecordIdOrderByIdAsc(consumptionId);
        InventoryConsumptionDetailsResponse r = new InventoryConsumptionDetailsResponse();
        r.setRecord(map(record));
        r.setLines(lines.stream().map(this::map).toList());
        return ResponseEntity.ok(ApiResponse.success("Consumption", r));
    }

    @PostMapping("/consumptions/reprocess/{pedidoId}")
    public ResponseEntity<ApiResponse<InventoryConsumptionRecordResponse>> reprocess(@PathVariable Long pedidoId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        InventoryConsumptionRecord r = consumptionService.reprocess(ctx.tenantId(), pedidoId);
        return ResponseEntity.ok(ApiResponse.success("Reprocessado", map(r)));
    }

    private InventoryItemResponse map(InventoryItem item) {
        InventoryItemResponse r = new InventoryItemResponse();
        r.setId(item.getId());
        r.setName(item.getName());
        r.setSku(item.getSku());
        r.setType(item.getType());
        r.setCategory(item.getCategory());
        r.setBaseUnitCode(item.getBaseUnit() != null ? item.getBaseUnit().getCode() : null);
        r.setStockControlEnabled(item.getStockControlEnabled());
        r.setAllowNegativeStock(item.getAllowNegativeStock());
        r.setCurrentQuantity(item.getCurrentQuantity());
        r.setAverageCost(item.getAverageCost());
        r.setLastCost(item.getLastCost());
        r.setMinimumQuantity(item.getMinimumQuantity());
        r.setReorderQuantity(item.getReorderQuantity());
        r.setStatus(item.getStatus());
        return r;
    }

    private InventoryMovementResponse map(InventoryMovement mv) {
        InventoryMovementResponse r = new InventoryMovementResponse();
        r.setId(mv.getId());
        r.setInventoryItemId(mv.getInventoryItem() != null ? mv.getInventoryItem().getId() : null);
        r.setMovementType(mv.getMovementType());
        r.setDirection(mv.getDirection());
        r.setQuantity(mv.getQuantity());
        r.setUnit(mv.getUnit() != null ? mv.getUnit().getCode() : null);
        r.setQuantityBaseUnit(mv.getQuantityBaseUnit());
        r.setUnitCost(mv.getUnitCost());
        r.setTotalCost(mv.getTotalCost());
        r.setStockBefore(mv.getStockBefore());
        r.setStockAfter(mv.getStockAfter());
        r.setReferenceType(mv.getReferenceType());
        r.setReferenceId(mv.getReferenceId());
        r.setSource(mv.getSource());
        r.setReason(mv.getReason());
        r.setCreatedAt(mv.getCreatedAt());
        return r;
    }

    private InventoryConsumptionRecordResponse map(InventoryConsumptionRecord record) {
        InventoryConsumptionRecordResponse r = new InventoryConsumptionRecordResponse();
        r.setId(record.getId());
        r.setPedidoId(record.getPedido() != null ? record.getPedido().getId() : null);
        r.setPagamentoId(record.getPagamento() != null ? record.getPagamento().getId() : null);
        r.setStatus(record.getStatus());
        r.setTriggerType(record.getTriggerType());
        r.setConsumedAt(record.getConsumedAt());
        r.setGrossRevenueAmount(record.getGrossRevenueAmount());
        r.setNetRevenueAmount(record.getNetRevenueAmount());
        r.setTaxAmount(record.getTaxAmount());
        r.setTotalCost(record.getTotalCost());
        r.setEstimatedMarginAmount(record.getEstimatedMarginAmount());
        r.setEstimatedMarginPercentage(record.getEstimatedMarginPercentage());
        r.setWarningCount(record.getWarningCount());
        return r;
    }

    private InventoryConsumptionLineResponse map(InventoryConsumptionLine line) {
        InventoryConsumptionLineResponse r = new InventoryConsumptionLineResponse();
        r.setId(line.getId());
        r.setInventoryItemId(line.getInventoryItem() != null ? line.getInventoryItem().getId() : null);
        r.setProductId(line.getProduct() != null ? line.getProduct().getId() : null);
        r.setPedidoItemId(line.getPedidoItem() != null ? line.getPedidoItem().getId() : null);
        r.setRecipeId(line.getRecipe() != null ? line.getRecipe().getId() : null);
        r.setQuantityBaseUnit(line.getQuantityBaseUnit());
        r.setUnit(line.getUnit() != null ? line.getUnit().getCode() : null);
        r.setUnitCost(line.getUnitCost());
        r.setTotalCost(line.getTotalCost());
        r.setStockBefore(line.getStockBefore());
        r.setStockAfter(line.getStockAfter());
        r.setWarningCode(line.getWarningCode());
        return r;
    }

    private InventoryRecipeResponse map(InventoryRecipe recipe) {
        InventoryRecipeResponse r = new InventoryRecipeResponse();
        r.setId(recipe.getId());
        r.setProductId(recipe.getProduct() != null ? recipe.getProduct().getId() : null);
        r.setName(recipe.getName());
        r.setStatus(recipe.getStatus());
        r.setYieldQuantity(recipe.getYieldQuantity());
        r.setYieldUnit(recipe.getYieldUnit() != null ? recipe.getYieldUnit().getCode() : null);
        return r;
    }

    private InventoryRecipeLineResponse map(InventoryRecipeLine line) {
        InventoryRecipeLineResponse r = new InventoryRecipeLineResponse();
        r.setId(line.getId());
        r.setRecipeId(line.getRecipe() != null ? line.getRecipe().getId() : null);
        r.setInventoryItemId(line.getInventoryItem() != null ? line.getInventoryItem().getId() : null);
        r.setQuantity(line.getQuantity());
        r.setUnit(line.getUnit() != null ? line.getUnit().getCode() : null);
        r.setWastePercentage(line.getWastePercentage());
        r.setCostSnapshot(line.getCostSnapshot());
        return r;
    }

    private ProductInventoryMappingResponse map(ProductInventoryMapping m) {
        ProductInventoryMappingResponse r = new ProductInventoryMappingResponse();
        r.setId(m.getId());
        r.setProductId(m.getProduct() != null ? m.getProduct().getId() : null);
        r.setInventoryItemId(m.getInventoryItem() != null ? m.getInventoryItem().getId() : null);
        r.setRecipeId(m.getRecipe() != null ? m.getRecipe().getId() : null);
        r.setStockPolicy(m.getStockPolicy());
        r.setStatus(m.getStatus());
        return r;
    }

    private UnitOfMeasureResponse map(UnitOfMeasure u) {
        UnitOfMeasureResponse r = new UnitOfMeasureResponse();
        r.setId(u.getId());
        r.setCode(u.getCode());
        r.setName(u.getName());
        r.setType(u.getType());
        r.setDecimalAllowed(u.getDecimalAllowed());
        r.setStatus(u.getStatus());
        return r;
    }

    private UnitConversionResponse map(UnitConversion c) {
        UnitConversionResponse r = new UnitConversionResponse();
        r.setId(c.getId());
        r.setFromUnitId(c.getFromUnit() != null ? c.getFromUnit().getId() : null);
        r.setToUnitId(c.getToUnit() != null ? c.getToUnit().getId() : null);
        r.setFactor(c.getFactor());
        r.setStatus(c.getStatus());
        return r;
    }

    private TenantInventoryPolicyResponse map(TenantInventoryPolicy p) {
        TenantInventoryPolicyResponse r = new TenantInventoryPolicyResponse();
        r.setId(p.getId());
        r.setStockControlEnabled(p.getStockControlEnabled());
        r.setAllowNegativeStockDefault(p.getAllowNegativeStockDefault());
        r.setStrictStockRequiredForSale(p.getStrictStockRequiredForSale());
        r.setConsumptionTrigger(p.getConsumptionTrigger());
        r.setRequireRecipeForStockedProducts(p.getRequireRecipeForStockedProducts());
        r.setUseAverageCost(p.getUseAverageCost());
        r.setMarginCalculationBasis(p.getMarginCalculationBasis());
        r.setStatus(p.getStatus());
        return r;
    }
}
