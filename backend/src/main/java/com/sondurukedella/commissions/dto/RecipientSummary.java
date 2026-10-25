package com.sondurukedella.commissions.dto;

import java.math.BigDecimal;

// "By recipient": confirmed arrangements grouped by name (ignoring case and extra spaces). The name
// shown is the spelling on the most recent arrangement.
public record RecipientSummary(String recipient, long arrangements, BigDecimal committed, BigDecimal paid, BigDecimal outstanding) {
}
