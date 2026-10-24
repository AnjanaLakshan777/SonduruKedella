package com.sondurukedella.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final int SCALE = 2;

    private Money() {
    }

    // Request DTOs already reject more than 2 decimals, so this mostly just pins the scale
    // and lets compareTo/equals behave the same on 1500 and 1500.00.
    public static BigDecimal amount(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
