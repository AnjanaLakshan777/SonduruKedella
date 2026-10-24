package com.sondurukedella.inventory.dto;

import com.sondurukedella.inventory.entity.CostLayerOrigin;
import java.math.BigDecimal;
import java.time.LocalDate;

// A FIFO cost layer, in the order issues use them up. The stock value is the sum of remainingValue.
public record CostLayerResponse(
        long id,
        long materialId,
        CostLayerOrigin origin,
        long sourceMovementId,
        LocalDate date,
        BigDecimal originalQuantity,
        BigDecimal unitCost,
        BigDecimal originalCost,
        BigDecimal remainingQuantity,
        BigDecimal remainingValue) {
}
