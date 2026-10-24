package com.sondurukedella.revenue.dto;

import java.util.List;

public record ProjectReceivablesResponse(
        long projectId,
        List<InstallmentReceivableResponse> installments,
        ReceivableTotals totals) {
}
