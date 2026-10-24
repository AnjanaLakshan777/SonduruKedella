package com.sondurukedella.inventory.dto;

import java.math.BigDecimal;
import java.util.List;

// A project's material cost from the store: issued minus returned, at the cost fixed on each movement.
// Quantities and costs are positive here. This is only the stock part of project materials; manual
// Materials expenses and PROJECT supplier invoices are counted by their own modules.
public record ProjectMaterialUsageResponse(
        long projectId,
        List<Line> materials,
        BigDecimal issuedCost,
        BigDecimal returnedCost,
        BigDecimal netCost) {

    public record Line(
            long materialId,
            String sku,
            String name,
            String unit,
            BigDecimal issuedQuantity,
            BigDecimal issuedCost,
            BigDecimal returnedQuantity,
            BigDecimal returnedCost,
            BigDecimal netQuantity,
            BigDecimal netCost) {
    }
}
