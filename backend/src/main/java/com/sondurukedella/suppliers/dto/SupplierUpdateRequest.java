package com.sondurukedella.suppliers.dto;

import com.sondurukedella.common.validation.OptionalEmail;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Full replace of the editable details. Active/inactive has its own endpoint so an edit can't flip it
// by accident. Send back the version you loaded, otherwise you get a 409.
public record SupplierUpdateRequest(
        @NotBlank(message = "Enter the supplier name.") @Size(max = 200) String name,
        @Size(max = 150) String contactPerson,
        @NotBlank(message = "Enter a phone number.") @Size(max = 30) String phone,
        @OptionalEmail @Size(max = 150) String email,
        @Size(max = 300) String address,
        @Size(max = 300) String supplies,
        @NotNull @Min(0) @Max(3650) Integer creditDays,
        @NotNull Long version) {
}
