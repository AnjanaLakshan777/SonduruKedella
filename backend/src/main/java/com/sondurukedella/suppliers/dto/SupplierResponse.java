package com.sondurukedella.suppliers.dto;

import java.time.OffsetDateTime;

// Field names follow the frontend Supplier type; active, timestamps and version are extra.
public record SupplierResponse(
        long id,
        String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        String supplies,
        int creditDays,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {
}
