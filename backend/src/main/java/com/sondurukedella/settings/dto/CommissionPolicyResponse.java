package com.sondurukedella.settings.dto;

import java.math.BigDecimal;

/**
 * Commission policy; field names follow the frontend {@code Settings.commission} contract.
 * Each project type has a commission pool; the company share is derived (100 − pool).
 */
public record CommissionPolicyResponse(
        BigDecimal designCommissionPool,
        BigDecimal designCompanyShare,
        BigDecimal constructionCommissionPool,
        BigDecimal constructionCompanyShare,
        long version) {
}
