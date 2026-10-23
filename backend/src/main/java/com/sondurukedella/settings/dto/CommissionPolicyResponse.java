package com.sondurukedella.settings.dto;

import java.math.BigDecimal;

/**
 * Commission policy; field names follow the frontend {@code Settings.commission} contract.
 * {@code designCompanyShare} is derived (100 − pool). {@code constructionDefaultRate} is the optional
 * construction warning threshold.
 */
public record CommissionPolicyResponse(
        BigDecimal designCommissionPool,
        BigDecimal designCompanyShare,
        BigDecimal constructionDefaultRate,
        long version) {
}
