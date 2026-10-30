package com.sondurukedella.commissions.dto;

import java.time.LocalDate;

// voided ones only with includeVoided=true
public record CommissionPaymentFilter(Long projectId, Long commissionId, LocalDate from, LocalDate to, Boolean includeVoided) {
}
