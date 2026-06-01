package com.restaurante.testsupport;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class UniqueTestData {

    private static final AtomicLong COUNTER = new AtomicLong(System.nanoTime());

    private UniqueTestData() {
    }

    public static String uniqueSuffix() {
        long sequence = COUNTER.incrementAndGet();
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return Long.toString(sequence, 36) + random;
    }

    public static String uniqueSlug(String prefix) {
        return join(normalizeSlug(prefix, "tenant"), uniqueSuffix().toLowerCase(Locale.ROOT), 48, "-");
    }

    public static String uniqueTenantCode(String prefix) {
        return join(normalizeUpper(prefix, "TEN"), uniqueUpperToken(), 12, "");
    }

    public static String uniqueInstituicaoSigla(String prefix) {
        return join(normalizeUpper(prefix, "INS"), uniqueUpperToken(), 10, "");
    }

    public static String uniqueNif(String prefix) {
        return join(normalizeUpper(prefix, "NIF"), uniqueUpperToken(), 18, "-");
    }

    public static String uniqueUsername(String prefix) {
        return join(normalizeSlug(prefix, "user"), uniqueSuffix().toLowerCase(Locale.ROOT), 40, "-");
    }

    public static String uniqueEmail(String prefix) {
        return uniqueUsername(prefix) + "@consuma.test";
    }

    public static String uniqueTelefone() {
        long value = Math.floorMod(COUNTER.incrementAndGet(), 100_000_000L);
        return "+2449" + String.format(Locale.ROOT, "%08d", value);
    }

    public static String uniqueDeviceCode(String prefix) {
        return join(normalizeUpper(prefix, "DEV"), uniqueUpperToken(), 20, "-");
    }

    public static String uniqueQrCode(String prefix) {
        return join(normalizeUpper(prefix, "QR"), uniqueUpperToken(), 24, "-");
    }

    private static String uniqueUpperToken() {
        return uniqueSuffix().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private static String normalizeSlug(String value, String fallback) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String normalizeUpper(String value, String fallback) {
        String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String join(String prefix, String suffix, int maxLength, String separator) {
        int separatorLength = separator.length();
        int maxSuffixLength = Math.max(1, maxLength - separatorLength - 1);
        String trimmedSuffix = suffix.length() > maxSuffixLength
                ? suffix.substring(suffix.length() - maxSuffixLength)
                : suffix;
        int maxPrefixLength = Math.max(1, maxLength - separatorLength - trimmedSuffix.length());
        String trimmedPrefix = prefix.length() > maxPrefixLength ? prefix.substring(0, maxPrefixLength) : prefix;
        return trimmedPrefix + separator + trimmedSuffix;
    }
}
