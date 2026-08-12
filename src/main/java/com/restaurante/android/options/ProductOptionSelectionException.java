package com.restaurante.android.options;

/** Stable internal reason for future Quote handling; no HTTP contract is introduced in this phase. */
public class ProductOptionSelectionException extends IllegalArgumentException {
    public enum Reason { REQUIRED_GROUP_UNSATISFIED, TOO_MANY_SELECTIONS, DUPLICATE_OPTION, UNKNOWN_OPTION,
        OTHER_GROUP, OTHER_PRODUCT, OTHER_TENANT, INACTIVE_OPTION, UNAVAILABLE_OPTION, OPTIONS_NOT_PROJECTABLE }
    private final Reason reason;
    public ProductOptionSelectionException(Reason reason) { super(reason.name()); this.reason = reason; }
    public Reason getReason() { return reason; }
}
