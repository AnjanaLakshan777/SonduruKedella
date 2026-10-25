package com.sondurukedella.reports.dto;

import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.inventory.dto.MaterialResponse;
import java.math.BigDecimal;
import java.time.LocalDate;

// The store's FIFO value (Σ remaining value of open cost layers, MaterialService.valuation) and a page
// of materials with their own layer value. Never the default unit cost, an average, or purchases to date.
public record InventoryValuationReport(LocalDate asOf, long materials, long materialsInStock, BigDecimal stockValue,
        PageResponse<MaterialResponse> rows) {
}
