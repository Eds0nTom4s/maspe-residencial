package com.restaurante.inventory.listener;

import com.restaurante.exception.BusinessException;
import com.restaurante.fiscal.corrections.event.FiscalCreditNoteIssuedForInventoryReturnEvent;
import com.restaurante.inventory.service.InventoryReturnService;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.enums.InventoryRestockPolicy;
import com.restaurante.model.enums.InventoryReturnReasonCategory;
import com.restaurante.model.enums.InventoryReturnSource;
import com.restaurante.model.enums.InventoryReturnType;
import com.restaurante.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class InventoryReturnOnFiscalCreditNoteIssuedListener {

    private final PedidoRepository pedidoRepository;
    private final InventoryReturnService returnService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreditNoteIssued(FiscalCreditNoteIssuedForInventoryReturnEvent event) {
        if (event == null || event.tenantId() == null || event.pedidoId() == null) return;

        Pedido pedido = pedidoRepository.findById(event.pedidoId()).orElse(null);
        if (pedido == null || pedido.getTenant() == null || !pedido.getTenant().getId().equals(event.tenantId())) return;

        if (event.correctionSource() == null) return;

        // MVP: criar retorno apenas para PRODUCT_RETURN; PARTIAL_REFUND exige revisão/inputs mais detalhados.
        if (event.correctionSource() != com.restaurante.model.enums.FiscalCorrectionSource.PRODUCT_RETURN) return;

        List<InventoryReturnService.RequestedReturnLine> lines = new ArrayList<>();
        if (pedido.getItens() != null) {
            for (var it : pedido.getItens()) {
                if (it == null || it.getId() == null) continue;
                int qty = it.getQuantidade();
                if (qty <= 0) continue;
                lines.add(new InventoryReturnService.RequestedReturnLine(it.getId(), BigDecimal.valueOf(qty), InventoryRestockPolicy.MANUAL_REVIEW));
            }
        }

        if (lines.isEmpty()) return;

        try {
            returnService.createReturn(
                    event.tenantId(),
                    event.pedidoId(),
                    InventoryReturnType.FISCAL_CREDIT_NOTE_LINKED,
                    InventoryReturnReasonCategory.CUSTOMER_RETURN,
                    "Criado a partir de nota interna de crédito (PRODUCT_RETURN)",
                    lines,
                    InventoryReturnSource.FISCAL_CORRECTION,
                    null
            );
        } catch (BusinessException ignored) {
            // Ignorar falha: devolução de stock não deve quebrar ciclo fiscal.
        }
    }
}

