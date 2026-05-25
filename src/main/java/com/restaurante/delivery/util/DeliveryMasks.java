package com.restaurante.delivery.util;

public final class DeliveryMasks {
    private DeliveryMasks() {}

    public static String maskPlate(String plate) {
        if (plate == null || plate.isBlank()) return null;
        String p = plate.trim().replaceAll("\\s+", "");
        if (p.length() <= 3) return "***";
        String prefix = p.substring(0, Math.min(2, p.length()));
        String suffix = p.substring(Math.max(p.length() - 2, 0));
        return prefix + "***" + suffix;
    }
}

