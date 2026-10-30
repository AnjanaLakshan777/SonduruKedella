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

// total is worked out from the lines. dueDate defaults to invoice date + the supplier's credit days.
// initialPayment = "paid now" on the form, saved in the same transaction.
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

    // stock lines need a material, in that material's unit
    public record Line(
            Long materialId,
            @Size(max = 300) String description,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotNull Long unitId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal unitPrice) {
    }
}
