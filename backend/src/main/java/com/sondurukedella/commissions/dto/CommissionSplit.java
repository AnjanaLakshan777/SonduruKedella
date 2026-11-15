package com.sondurukedella.commissions.dto;

import java.math.BigDecimal;

// the project's commission pool vs the company share (design and construction alike).
// company share = contract value - pool, so the two always add up (the extra cent goes to the company).
// unallocated goes negative when they confirmed over the pool.
public record CommissionSplit(
        BigDecimal poolPct,
        BigDecimal poolAmount,
        BigDecimal companySharePct,
        BigDecimal companyShareAmount,
        BigDecimal allocatedAmount,
        BigDecimal unallocatedAmount) {
}
