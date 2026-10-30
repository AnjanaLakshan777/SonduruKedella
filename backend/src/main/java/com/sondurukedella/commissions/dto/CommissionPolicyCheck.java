package com.sondurukedella.commissions.dto;

import java.math.BigDecimal;

// total rate vs the design pool (or construction level, if one is set). Only a warning.
public record CommissionPolicyCheck(BigDecimal limitPct, BigDecimal totalRatePct, boolean exceeded, String warning) {
}
