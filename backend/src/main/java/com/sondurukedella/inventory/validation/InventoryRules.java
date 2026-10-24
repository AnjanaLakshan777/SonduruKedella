package com.sondurukedella.inventory.validation;

import com.sondurukedella.common.exception.InvalidRequestException;
import java.math.BigDecimal;

public final class InventoryRules {

    // the adjust-stock dialog's rule: say what happened, not just "fix"
    public static final int MIN_ADJUSTMENT_REASON = 5;

    private InventoryRules() {
    }

    public static String adjustmentReason(String reason) {
        String trimmed = reason == null ? "" : reason.strip();
        if (trimmed.length() < MIN_ADJUSTMENT_REASON) {
            throw new InvalidRequestException("Give a reason for the adjustment (count difference, damage...).");
        }
        return trimmed;
    }

    public static void nonZero(BigDecimal quantityChange) {
        if (quantityChange.signum() == 0) {
            throw new InvalidRequestException("An adjustment must add or remove some stock.");
        }
    }

    // 12.500 -> "12.5", for messages
    public static String quantity(BigDecimal value) {
        return value.abs().stripTrailingZeros().toPlainString();
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
