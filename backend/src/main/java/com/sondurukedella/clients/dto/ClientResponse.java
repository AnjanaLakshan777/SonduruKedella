package com.sondurukedella.clients.dto;

import com.sondurukedella.clients.entity.ClientKind;
import java.time.OffsetDateTime;

public record ClientResponse(
        long id,
        String name,
        ClientKind kind,
        String phone,
        String email,
        String address,
        String city,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {
}
