package com.restaurante.inventory;

import com.restaurante.inventory.service.InventoryReturnFinancialCalculator;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.InventoryReturnLine;
import com.restaurante.model.entity.InventoryReturnRecord;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.enums.FiscalDocumentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "consuma.inventory.enabled=true"
})
public class InventoryReturnFinancialCalculatorTest {

    @Autowired private InventoryReturnFinancialCalculator calculator;

    @Test
    void calculaReversoPorCreditNote() {
        FiscalDocument credit = new FiscalDocument();
        credit.setDocumentType(FiscalDocumentType.INTERNAL_CREDIT_NOTE);
        credit.setTaxableAmount(new BigDecimal("100.00"));
        credit.setExemptAmount(new BigDecimal("0.00"));
        credit.setTaxAmount(new BigDecimal("14.00"));
        credit.setTotalAmount(new BigDecimal("114.00"));

        InventoryReturnRecord rr = new InventoryReturnRecord();
        rr.setFiscalCreditNote(credit);

        var totals = calculator.calculate(rr, List.of());
        assertThat(totals.revenueReversed()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(totals.taxReversed()).isEqualByComparingTo(new BigDecimal("14.00"));
        assertThat(totals.estimated()).isFalse();
    }

    @Test
    void fallbackSemDocumentoFiscalEstimado() {
        InventoryReturnRecord rr = new InventoryReturnRecord();
        InventoryReturnLine line = new InventoryReturnLine();
        ItemPedido it = new ItemPedido();
        it.setPrecoUnitario(new BigDecimal("50.00"));
        line.setPedidoItem(it);
        line.setQuantityReturned(new BigDecimal("1"));

        var totals = calculator.calculate(rr, List.of(line));
        assertThat(totals.revenueReversed()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(totals.taxReversed()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totals.estimated()).isTrue();
    }
}

