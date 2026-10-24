package com.sondurukedella.inventory.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// quantityOnHand is the material's last ledger row; stockValue is the sum of its open FIFO layers
// (which every posting checks equals the ledger's value). defaultUnitCost is the catalogue's
// reference price (materials.unit_cost): it suggests a receipt price and prices nothing.
public record MaterialResponse(
        long id,
        String sku,
        String name,
        String category,
        long unitId,
        String unit,
        BigDecimal defaultUnitCost,
        BigDecimal quantityOnHand,
        BigDecimal stockValue,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {
}
