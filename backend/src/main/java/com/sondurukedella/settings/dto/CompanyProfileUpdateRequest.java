package com.sondurukedella.settings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Saves the "Company information" section. The financial year start is read-only and not accepted here.
 * {@code version} must match the current record (optimistic locking).
 */
public record CompanyProfileUpdateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String tagline,
        @Size(max = 300) String address,
        @Size(max = 30) String phone,
        @Email @Size(max = 150) String email,
        @Size(max = 50) String registration,
        @Size(max = 150) String owner,
        @NotNull @Min(0) Integer supplierCreditDays,
        @NotNull Long version) {
}
