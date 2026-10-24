package com.sondurukedella.revenue.dto;

import java.util.List;

// receipts include voided ones (flagged), so the history is complete; they don't count in the totals
public record InstallmentReceivableDetail(
        InstallmentReceivableResponse installment,
        List<ReceiptResponse> receipts,
        List<WriteOffResponse> writeOffs) {
}
