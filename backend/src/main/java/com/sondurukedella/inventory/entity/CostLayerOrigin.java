package com.sondurukedella.inventory.entity;

public enum CostLayerOrigin {
    RECEIPT,
    // a positive adjustment, at the unit cost given with it
    ADJUSTMENT,
    // V4: a material's stock and value when FIFO started, carried forward as one layer
    LEGACY_BALANCE,
    // a return against an issue posted before FIFO, which has no layers to go back to
    LEGACY_RETURN
}
