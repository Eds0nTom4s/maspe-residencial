package com.restaurante.delivery.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class GeoDistance {
    private GeoDistance() {}

    // Simple haversine distance in KM
    public static BigDecimal distanceKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return null;
        double r = 6371.0d;
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue())) * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double km = r * c;
        return BigDecimal.valueOf(km).setScale(3, RoundingMode.HALF_UP);
    }
}

