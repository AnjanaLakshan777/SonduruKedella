package com.sondurukedella.suppliers.dto;

import com.sondurukedella.common.validation.OptionalEmail;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The "Add supplier" dialog. Only name and phone are required, as in the dialog - plenty of local
// hardware shops have no email. creditDays defaults to the company setting (Settings > Company).
public record SupplierCreateRequest(
        @NotBlank(message = "Enter the supplier name.") @Size(max = 200) String name,
        @Size(max = 150) String contactPerson,
        @NotBlank(message = "Enter a phone number.") @Size(max = 30) String phone,
        @OptionalEmail @Size(max = 150) String email,
        @Size(max = 300) String address,
        @Size(max = 300) String supplies,
        @Min(0) @Max(3650) Integer creditDays) {
}
