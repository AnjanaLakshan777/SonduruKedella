package com.sondurukedella.inventory.dto;

import com.sondurukedella.inventory.entity.CostingMethod;
import com.sondurukedella.inventory.entity.MovementType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Signed like the ledger: quantityChange and totalCost are negative for anything leaving stock.
// For an ISSUED row, returnedQuantity/returnableQuantity show what has come back and what still can;
// they are null on other types. The frontend's StockMovement calls quantityChange "qty".
// FIFO: totalCost is the sum of the allocations; on a movement that drew from several layers unitCost
// is just totalCost / quantity (4 decimals), for display. costLayerId is the layer a receipt or positive
// adjustment created. costingMethod WAC marks movements posted before FIFO (they have no allocations).
public record StockMovementResponse(
        long id,
        long materialId,
        String material,
        LocalDate date,
        MovementType type,
        BigDecimal quantityChange,
        BigDecimal unitCost,
        BigDecimal totalCost,
        BigDecimal quantityAfter,
        BigDecimal valueAfter,
        Long projectId,
        Long supplierId,
        String supplier,
        Long returnedFromMovementId,
        BigDecimal returnedQuantity,
        BigDecimal returnableQuantity,
        String reference,
        String notes,
        UUID requestId,
        OffsetDateTime createdAt,
        CostingMethod costingMethod,
        Long costLayerId,
        List<CostAllocationResponse> allocations) {
}
