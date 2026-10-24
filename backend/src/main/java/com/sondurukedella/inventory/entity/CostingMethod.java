package com.sondurukedella.inventory.entity;

// How a movement was priced. WAC marks rows posted before V4 (the moving weighted average); they keep
// the cost they were posted at. Everything since is FIFO.
public enum CostingMethod {
    WAC,
    FIFO
}
