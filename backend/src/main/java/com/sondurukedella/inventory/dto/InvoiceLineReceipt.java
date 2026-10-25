package com.sondurukedella.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// One line of a STOCK supplier invoice, received into stock at the line's unit price. Only the
// suppliers module builds these, inside the transaction that saves the invoice.
public record InvoiceLineReceipt(long invoiceLineId, long materialId, long unitId, LocalDate date, BigDecimal quantity,
        BigDecimal unitPrice, long supplierId, String invoiceNo) {
}
