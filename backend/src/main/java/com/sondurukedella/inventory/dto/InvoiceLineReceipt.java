package com.sondurukedella.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// a STOCK invoice line coming into stock at its unit price - only the suppliers module creates these
public record InvoiceLineReceipt(long invoiceLineId, long materialId, long unitId, LocalDate date, BigDecimal quantity,
        BigDecimal unitPrice, long supplierId, String invoiceNo) {
}
