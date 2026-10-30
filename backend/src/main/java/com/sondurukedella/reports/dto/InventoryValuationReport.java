package com.sondurukedella.reports.dto;

import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.inventory.dto.MaterialResponse;
import java.math.BigDecimal;
import java.time.LocalDate;

// FIFO layer value - not the default unit cost or total purchases
public record InventoryValuationReport(LocalDate asOf, long materials, long materialsInStock, BigDecimal stockValue,
        PageResponse<MaterialResponse> rows) {
}
