package com.sondurukedella.suppliers.dto;

import com.sondurukedella.suppliers.dto.SupplierInvoiceResponse.InvoiceStatus;
import com.sondurukedella.suppliers.entity.InvoiceAllocation;
import java.time.LocalDate;

// All optional. from/to bound the invoice date, inclusive.
public record SupplierInvoiceFilter(Long supplierId, Long projectId, InvoiceAllocation allocation, InvoiceStatus status,
        LocalDate from, LocalDate to) {
}
