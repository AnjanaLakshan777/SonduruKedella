package com.sondurukedella.revenue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Reversing a write-off puts the amount back on what the client owes; the write-off stays, marked reversed.
public record WriteOffReversalRequest(
        @NotBlank(message = "Give the reason the write-off is being reversed.") String reason,
        @NotNull Long version) {
}
