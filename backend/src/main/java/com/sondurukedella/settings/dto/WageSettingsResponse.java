package com.sondurukedella.settings.dto;

import com.sondurukedella.settings.entity.Payday;
import java.math.BigDecimal;

/** Wage defaults; field names follow the frontend {@code Settings.wages} contract. */
public record WageSettingsResponse(
        BigDecimal defaultDailyRate,
        BigDecimal hoursPerDay,
        BigDecimal overtimeMultiplier,
        Payday payday,
        long version) {
}
