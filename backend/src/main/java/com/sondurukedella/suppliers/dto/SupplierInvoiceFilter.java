package com.sondurukedella.suppliers.dto;

import com.sondurukedella.suppliers.dto.SupplierInvoiceResponse.InvoiceStatus;
import com.sondurukedella.suppliers.entity.InvoiceAllocation;
import java.time.LocalDate;

// from/to = invoice date
public record SupplierInvoiceFilter(Long supplierId, Long projectId, InvoiceAllocation allocation, InvoiceStatus status,
        LocalDate from, LocalDate to) {
}
