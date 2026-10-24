package com.sondurukedella.clients.dto;

import com.sondurukedella.clients.entity.ClientKind;
import com.sondurukedella.common.validation.OptionalEmail;
import com.sondurukedella.clients.validation.PhoneNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Field names match the frontend Client type so the dialog can post its state as-is.
public record ClientCreateRequest(
        @NotBlank(message = "Enter the client’s name.") @Size(max = 200) String name,
        @NotNull ClientKind kind,
        @NotNull(message = "Enter a phone number, e.g. 071 234 5678.") @PhoneNumber String phone,
        @OptionalEmail @Size(max = 150) String email,
        @Size(max = 300) String address,
        @Size(max = 100) String city,
        String notes) {
}
