package com.sondurukedella.agreements.dto;

import java.time.OffsetDateTime;

// fileName, size and uploadedAt match the frontend AgreementDoc type. The storage key and any
// server path are deliberately left out.
public record AgreementResponse(
        long id,
        long projectId,
        String fileName,
        String contentType,
        long size,
        String sha256,
        OffsetDateTime uploadedAt,
        long version) {
}
