package com.sondurukedella.inventory.dto;

import com.sondurukedella.inventory.entity.CostLayerOrigin;
import java.math.BigDecimal;
import java.time.LocalDate;

// One layer's part in a movement. Signed like the ledger: a draw (issue, negative adjustment) is
// negative, stock put back by a return is positive and names the draw it reverses. unitCost is the layer's.
public record CostAllocationResponse(
        long id,
        long layerId,
        long layerSourceMovementId,
        CostLayerOrigin layerOrigin,
        LocalDate layerDate,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal cost,
        Long reversesAllocationId) {
}
