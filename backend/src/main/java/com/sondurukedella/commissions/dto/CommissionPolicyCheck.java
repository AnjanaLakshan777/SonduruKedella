package com.sondurukedella.commissions.dto;

import java.math.BigDecimal;

// The project's live rates against the policy: the design commission pool, or the optional
// construction warning level. limitPct is null when there is no level (construction without one).
// A warning only; the owner may confirm anyway, which marks the rows policyException.
public record CommissionPolicyCheck(BigDecimal limitPct, BigDecimal totalRatePct, boolean exceeded, String warning) {
}
