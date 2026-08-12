package com.restaurante.android.options;

import com.restaurante.model.entity.ProductOption;
import java.math.BigDecimal;
import java.util.List;

/** Exact selection result reusable by a future quote service. */
public record ProductOptionSelection(List<ProductOption> options, BigDecimal additionalPrice) {
}
