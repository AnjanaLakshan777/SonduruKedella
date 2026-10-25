package com.sondurukedella.suppliers.entity;

// STOCK: goods into the store (stock value; cost only when issued). PROJECT: goods delivered straight
// to a construction project (project material cost at once, no stock movement).
public enum InvoiceAllocation {
    STOCK,
    PROJECT
}
