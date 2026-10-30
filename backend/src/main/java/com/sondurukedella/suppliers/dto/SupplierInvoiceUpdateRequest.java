package com.sondurukedella.suppliers.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

// Due date and notes can always change. The rest only on a project invoice with no payments -
// for stock or paid invoices send those fields unchanged.
public record SupplierInvoiceUpdateRequest(
        @NotNull Long version,
        @NotBlank(message = "Enter the invoice number.") @Size(max = 100) String invoiceNo,
        @NotNull(message = "Enter the invoice date.") LocalDate invoiceDate,
        @NotNull(message = "Enter the due date.") LocalDate dueDate,
        Long projectId,
        String notes,
        @NotEmpty(message = "Add at least one line.") @Size(max = 200) @Valid List<SupplierInvoiceCreateRequest.Line> lines) {
}
