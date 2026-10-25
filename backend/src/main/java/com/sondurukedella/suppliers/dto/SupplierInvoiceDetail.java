package com.sondurukedella.suppliers.dto;

import java.math.BigDecimal;
import java.util.List;

public record SupplierInvoiceDetail(SupplierInvoiceResponse invoice, List<Line> lines, List<SupplierPaymentResponse> payments) {

    public record Line(long id, int lineNo, Long materialId, String description, BigDecimal quantity, long unitId, String unit,
            BigDecimal unitPrice, BigDecimal lineAmount) {
    }
}
