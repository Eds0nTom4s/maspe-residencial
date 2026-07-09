package com.restaurante.billing.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BillingMath {
    private BillingMath() {
    }

    public static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    public static BigDecimal scaleMoney(BigDecimal v) {
        return nz(v).setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal scaleQty(BigDecimal v) {
        return nz(v).setScale(6, RoundingMode.HALF_UP);
    }
}

