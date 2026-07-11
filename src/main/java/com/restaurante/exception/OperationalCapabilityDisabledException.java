package com.restaurante.exception;

public class OperationalCapabilityDisabledException extends RuntimeException {

    public static final String KDS_DISABLED_CODE = "KDS_DISABLED_FOR_OPERATION";
    public static final String PRODUCTION_DISABLED_CODE = "PRODUCTION_DISABLED_FOR_OPERATION";

    private final String code;

    private OperationalCapabilityDisabledException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static OperationalCapabilityDisabledException kds() {
        return new OperationalCapabilityDisabledException(
                KDS_DISABLED_CODE,
                "O KDS não está activo para esta operação."
        );
    }

    public static OperationalCapabilityDisabledException production() {
        return new OperationalCapabilityDisabledException(
                PRODUCTION_DISABLED_CODE,
                "Este pedido não utiliza fluxo de produção."
        );
    }

    public String getCode() {
        return code;
    }
}

