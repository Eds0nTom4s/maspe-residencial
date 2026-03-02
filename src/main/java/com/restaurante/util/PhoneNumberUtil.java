package com.restaurante.util;

/**
 * Utilitário para normalização e validação de números de telefone angolanos.
 * 
 * Formatos aceitos:
 * - 923456789 (formato local)
 * - 0923456789 (com zero inicial)
 * - 244923456789 (com código do país)
 * - +244923456789 (formato internacional)
 * 
 * Formato de saída normalizado: +244XXXXXXXXX
 */
public class PhoneNumberUtil {
    
    private static final String COUNTRY_CODE = "244";
    private static final String INTERNATIONAL_PREFIX = "+";
    
    /**
     * Normaliza um número de telefone para o formato internacional padrão: +244XXXXXXXXX
     * 
     * @param phoneNumber O número de telefone em qualquer formato aceito
     * @return O número normalizado no formato +244XXXXXXXXX
     * @throws IllegalArgumentException Se o número for nulo, vazio ou inválido
     */
    public static String normalize(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Número de telefone não pode ser vazio");
        }
        
        // Remove espaços, hífens, parênteses
        String cleaned = phoneNumber.replaceAll("[\\s\\-()]", "");
        
        // Remove o símbolo + se existir
        if (cleaned.startsWith(INTERNATIONAL_PREFIX)) {
            cleaned = cleaned.substring(1);
        }
        
        // Se não começar com código do país, adiciona
        if (!cleaned.startsWith(COUNTRY_CODE)) {
            // Remove zero inicial se existir (ex: 0923456789 -> 923456789)
            if (cleaned.startsWith("0")) {
                cleaned = cleaned.substring(1);
            }
            cleaned = COUNTRY_CODE + cleaned;
        }
        
        // Valida o comprimento final (244 + 9 dígitos = 12 caracteres)
        if (cleaned.length() != 12) {
            throw new IllegalArgumentException("Número de telefone inválido. Esperado 9 dígitos após o código do país.");
        }
        
        // Retorna no formato internacional: +244XXXXXXXXX
        return INTERNATIONAL_PREFIX + cleaned;
    }
    
    /**
     * Verifica se um número de telefone é válido para Angola.
     * 
     * @param phoneNumber O número a validar
     * @return true se válido, false caso contrário
     */
    public static boolean isValid(String phoneNumber) {
        try {
            String normalized = normalize(phoneNumber);
            // Verifica se tem exatamente 13 caracteres (+244XXXXXXXXX)
            return normalized.length() == 13;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Formata um número de telefone para exibição: +244 923 456 789
     * 
     * @param phoneNumber O número a formatar
     * @return O número formatado para exibição
     */
    public static String format(String phoneNumber) {
        String normalized = normalize(phoneNumber);
        // +244 923 456 789
        return String.format("+%s %s %s %s",
            normalized.substring(1, 4),  // 244
            normalized.substring(4, 7),  // 923
            normalized.substring(7, 10), // 456
            normalized.substring(10, 13) // 789
        );
    }
}
