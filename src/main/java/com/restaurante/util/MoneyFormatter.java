package com.restaurante.util;

import com.restaurante.config.CurrencyConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Component
public class MoneyFormatter {

    private static CurrencyConfig config;

    @Autowired
    public MoneyFormatter(CurrencyConfig config) {
        MoneyFormatter.config = config;
    }

    /**
     * Retorna a formatação monetária segura.
     * Se o valor for null, usa zero como predefinição lógica sem gerar NullPointerException.
     */
    public static String format(BigDecimal valor) {
        BigDecimal seguro = valor != null ? valor : BigDecimal.ZERO;
        
        if (config == null) {
            // Fallback de segurança caso a injeção do Spring não esteja acessível localmente
            return String.format("AOA %.2f", seguro);
        }

        // Suporta tanto o dash como underscore (pt_AO vs pt-AO)
        Locale localeFormat = new Locale.Builder()
                .setLanguageTag(config.getLocale().replace("_", "-"))
                .build();
                
        NumberFormat nf = NumberFormat.getNumberInstance(localeFormat);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);

        return config.getSymbol() + " " + nf.format(seguro);
    }
    
    /**
     * Retorna a configuração global de Code para fins transacionais caso requerido.
     */
    public static String getCurrencyCode() {
        if (config == null) return "AOA";
        return config.getCode();
    }
}
