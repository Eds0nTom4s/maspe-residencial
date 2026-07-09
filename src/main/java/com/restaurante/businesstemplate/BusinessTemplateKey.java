package com.restaurante.businesstemplate;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identificador (code + version) de BusinessTemplate.
 *
 * Formatos aceitos:
 * - CONSUMA_PONTO_V1  => code=CONSUMA_PONTO, version=1
 * - CONSUMA_REST_V1   => code=CONSUMA_REST,  version=1
 * - CONSUMA_PONTO     => code=CONSUMA_PONTO, version=null (resolve para latest suportado)
 */
public record BusinessTemplateKey(String code, Integer version) {

    private static final Pattern V_PATTERN = Pattern.compile("^([A-Z0-9_\\-]+)_V(\\d+)$");

    public static BusinessTemplateKey parse(String raw) {
        if (raw == null) return new BusinessTemplateKey(null, null);
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.isBlank()) return new BusinessTemplateKey(null, null);

        Matcher m = V_PATTERN.matcher(s);
        if (m.matches()) {
            String code = m.group(1);
            Integer v = Integer.parseInt(m.group(2));
            return new BusinessTemplateKey(code, v);
        }
        return new BusinessTemplateKey(s, null);
    }
}

