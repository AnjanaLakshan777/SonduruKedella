package com.sondurukedella.revenue.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// The "Delay deduction terms" dialog on a project. enabled=false is "Remove terms": the row stays,
// switched off. version is the terms' own, or null when the project has none yet.
public record DelayTermsUpdateRequest(
        @NotNull Boolean enabled,
        @NotNull @Valid DelayTermsDraft terms,
        Long version) {
}
