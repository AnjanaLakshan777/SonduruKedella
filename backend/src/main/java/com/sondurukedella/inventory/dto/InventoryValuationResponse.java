package com.sondurukedella.inventory.dto;

import java.math.BigDecimal;

// Company store totals, from the open FIFO layers. Inactive materials still count if
// they hold stock - deactivating doesn't make the stock disappear.
public record InventoryValuationResponse(
        long materials,
        long materialsInStock,
        BigDecimal stockValue) {
}
