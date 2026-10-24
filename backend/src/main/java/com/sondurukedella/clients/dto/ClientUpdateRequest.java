package com.sondurukedella.clients.dto;

import com.sondurukedella.clients.entity.ClientKind;
import com.sondurukedella.clients.validation.ClientText;
import com.sondurukedella.clients.validation.PhoneNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Full replace, not a patch. Send back the version you loaded, otherwise you get a 409.
public record ClientUpdateRequest(
        @NotBlank(message = "Enter the client’s name.") @Size(max = 200) String name,
        @NotNull ClientKind kind,
        @NotNull(message = "Enter a phone number, e.g. 071 234 5678.") @PhoneNumber String phone,
        @Email(regexp = ClientText.EMAIL_PATTERN, message = "Check the email address.")
        @Size(max = 150) String email,
        @Size(max = 300) String address,
        @Size(max = 100) String city,
        String notes,
        @NotNull Long version) {
}
