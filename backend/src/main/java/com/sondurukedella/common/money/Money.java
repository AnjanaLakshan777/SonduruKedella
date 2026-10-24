package com.sondurukedella.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Money {

    public static final int SCALE = 2;

    private Money() {
    }

    // Request DTOs already reject more than 2 decimals, so this mostly just pins the scale
    // and lets compareTo/equals behave the same on 1500 and 1500.00.
    public static BigDecimal amount(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    // For activity messages, e.g. "LKR 1,850,000.00". Fixed US symbols so the server locale can't
    // turn it into "1.850.000,00".
    public static String format(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "LKR " + format.format(amount(value));
    }

    // true when the value has more decimals than money allows (1.005), ignoring trailing zeros (1.500)
    public static boolean hasTooManyDecimals(BigDecimal value) {
        return value.stripTrailingZeros().scale() > SCALE;
    }
}
