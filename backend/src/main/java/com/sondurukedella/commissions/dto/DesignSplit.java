package com.sondurukedella.commissions.dto;

import java.math.BigDecimal;

// Design projects only. poolAmount = contract value × pool % (rounded half-up to the cent) and
// companyShareAmount = contract value − poolAmount, so the two always add up to the contract value
// exactly. allocatedAmount is the sum of the live arrangements' amounts; unallocatedAmount is
// pool − allocated (negative when the owner confirmed an exception).
public record DesignSplit(
        BigDecimal poolPct,
        BigDecimal poolAmount,
        BigDecimal companySharePct,
        BigDecimal companyShareAmount,
        BigDecimal allocatedAmount,
        BigDecimal unallocatedAmount) {
}
