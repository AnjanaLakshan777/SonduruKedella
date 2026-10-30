package com.sondurukedella.commissions.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// The whole set from the Commissions tab. Drafts that aren't sent get removed; confirmed ones must
// be sent (void them instead). acceptPolicyException = "confirm anyway" when over the policy.
public record CommissionSetRequest(
        @NotNull Boolean confirm,
        Boolean acceptPolicyException,
        @NotNull @Valid List<CommissionRowRequest> arrangements) {
}
