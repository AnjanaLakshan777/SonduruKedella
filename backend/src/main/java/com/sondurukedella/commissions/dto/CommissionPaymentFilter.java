package com.sondurukedella.commissions.dto;

import java.time.LocalDate;

// All optional. Voided payments are left out unless includeVoided is true.
public record CommissionPaymentFilter(Long projectId, Long commissionId, LocalDate from, LocalDate to, Boolean includeVoided) {
}
