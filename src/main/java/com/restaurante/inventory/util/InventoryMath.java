package com.restaurante.inventory.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class InventoryMath {
    private InventoryMath() {}

    public static BigDecimal scale(BigDecimal value, int scale, RoundingMode roundingMode) {
        if (value == null) return null;
        return value.setScale(scale, roundingMode);
    }
}

