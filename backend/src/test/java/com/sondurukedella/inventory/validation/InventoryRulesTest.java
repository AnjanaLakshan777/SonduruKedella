package com.sondurukedella.inventory.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sondurukedella.common.exception.InvalidRequestException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InventoryRulesTest {

    @Test
    void anAdjustmentNeedsARealReason() {
        assertThatThrownBy(() -> InventoryRules.adjustmentReason("  fix ")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> InventoryRules.adjustmentReason(null)).isInstanceOf(InvalidRequestException.class);
        assertThat(InventoryRules.adjustmentReason("  4 bags hardened  ")).isEqualTo("4 bags hardened");
    }

    @Test
    void anAdjustmentMustMoveSomething() {
        assertThatThrownBy(() -> InventoryRules.nonZero(new BigDecimal("0.000"))).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void quantitiesReadNaturallyInMessages() {
        assertThat(InventoryRules.quantity(new BigDecimal("-12.500"))).isEqualTo("12.5");
        assertThat(InventoryRules.quantity(new BigDecimal("40.000"))).isEqualTo("40");
    }
}
