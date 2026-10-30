package com.sondurukedella.commissions.dto;

import java.math.BigDecimal;

// grouped by name ignoring case/spaces, shows the latest spelling
public record RecipientSummary(String recipient, long arrangements, BigDecimal committed, BigDecimal paid, BigDecimal outstanding) {
}
