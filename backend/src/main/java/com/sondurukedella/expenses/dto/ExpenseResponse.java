package com.sondurukedella.expenses.dto;

import com.sondurukedella.expenses.entity.ExpenseScope;
import com.sondurukedella.settings.entity.ExpenseGroup;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// paid and outstanding are worked out from the active payments, never taken from the client.
// The frontend's Expense keeps the category-specific fields in a free-form "details" map; here they
// are typed columns (quantity/unit/unitPrice, machineType/usage*, billingPeriod), null when unused.
public record ExpenseResponse(
        long id,
        LocalDate date,
        ExpenseScope scope,
        Long projectId,
        ExpenseGroup group,
        long categoryId,
        String category,
        String description,
        String payee,
        Long supplierId,
        String supplier,
        BigDecimal amount,
        LocalDate dueDate,
        String invoiceNo,
        String notes,
        BigDecimal quantity,
        Long unitId,
        String unit,
        BigDecimal unitPrice,
        String machineType,
        LocalDate usageFrom,
        LocalDate usageTo,
        String billingPeriod,
        // the receipt or bill PDF, if one was uploaded (download it from /expenses/{id}/attachment/download)
        String attachmentFilename,
        Long attachmentSize,
        BigDecimal paid,
        BigDecimal outstanding,
        ExpensePaymentStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version,
        // only set on create/update, when a supplier invoice with the same supplier + number exists.
        // just a warning, the expense still saves.
        String warning) {

    public ExpenseResponse withWarning(String warning) {
        return new ExpenseResponse(id, date, scope, projectId, group, categoryId, category, description, payee, supplierId, supplier,
                amount, dueDate, invoiceNo, notes, quantity, unitId, unit, unitPrice, machineType, usageFrom, usageTo, billingPeriod, attachmentFilename, attachmentSize, paid,
                outstanding, status, createdAt, updatedAt, version, warning);
    }
}
