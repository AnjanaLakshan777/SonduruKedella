package com.sondurukedella.suppliers.dto;

import com.sondurukedella.suppliers.entity.InvoiceAllocation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// paid counts active payments only; outstanding = total − paid (0 once cancelled). status: UNPAID,
// PARTIALLY_PAID, PAID, OVERDUE when something is outstanding and the due date has passed (as the UI
// shows it), or CANCELLED. A cancelled invoice is no payable and no cost.
public record SupplierInvoiceResponse(
        long id,
        long supplierId,
        String supplierName,
        String invoiceNo,
        LocalDate invoiceDate,
        LocalDate dueDate,
        InvoiceAllocation allocation,
        Long projectId,
        BigDecimal total,
        BigDecimal paid,
        BigDecimal outstanding,
        InvoiceStatus status,
        String notes,
        OffsetDateTime cancelledAt,
        String cancelReason,
        String attachmentFilename,
        Long attachmentSize,
        OffsetDateTime createdAt,
        long version) {

    public enum InvoiceStatus { UNPAID, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED }
}
