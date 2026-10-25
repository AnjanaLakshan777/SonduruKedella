package com.sondurukedella.suppliers.dto;

import com.sondurukedella.suppliers.entity.InvoiceAllocation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Recording a supplier's invoice. The total is the sum of the lines (never sent). dueDate defaults to
// invoiceDate + the supplier's credit days. projectId only for PROJECT invoices (construction projects).
// initialPayment, when given, is recorded in the same transaction ("paid now" on the form).
public record SupplierInvoiceCreateRequest(
        @NotNull(message = "Choose the supplier.") Long supplierId,
        @NotBlank(message = "Enter the invoice number.") @Size(max = 100) String invoiceNo,
        @NotNull(message = "Enter the invoice date.") LocalDate invoiceDate,
        LocalDate dueDate,
        @NotNull(message = "Choose stock or project.") InvoiceAllocation allocation,
        Long projectId,
        String notes,
        @NotEmpty(message = "Add at least one line.") @Size(max = 200) @Valid List<Line> lines,
        @Valid SupplierPaymentRequest initialPayment) {

    // materialId is required on STOCK lines (and the unit must be the material's own); description
    // defaults to the material's name.
    public record Line(
            Long materialId,
            @Size(max = 300) String description,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotNull Long unitId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal unitPrice) {
    }
}
