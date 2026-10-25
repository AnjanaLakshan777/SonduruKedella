package com.sondurukedella.commissions.dto;

import java.util.List;

public record CommissionDetail(CommissionResponse commission, List<CommissionPaymentResponse> payments) {
}
