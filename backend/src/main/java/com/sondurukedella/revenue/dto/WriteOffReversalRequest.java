package com.sondurukedella.revenue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// the amount goes back on what the client owes; the write-off row stays, just marked reversed
public record WriteOffReversalRequest(
        @NotBlank(message = "Give the reason the write-off is being reversed.") String reason,
        @NotNull Long version) {
}
