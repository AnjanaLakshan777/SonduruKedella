package com.sondurukedella.commissions.dto;

import java.math.BigDecimal;

// total rate vs the project type's commission pool. Only a warning (confirmable as an exception).
public record CommissionPolicyCheck(BigDecimal limitPct, BigDecimal totalRatePct, boolean exceeded, String warning) {
}
