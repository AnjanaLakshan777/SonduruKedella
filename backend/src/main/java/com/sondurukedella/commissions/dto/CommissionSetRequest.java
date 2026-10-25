package com.sondurukedella.commissions.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// "Save draft" (confirm=false) or "Review & confirm" (confirm=true) on the project's Commissions tab.
// arrangements is the whole live set: drafts left out are removed; confirmed ones can't be left out
// (void them instead). acceptPolicyException is the owner's "confirm as an exception" when the rates
// add up to more than the policy level.
public record CommissionSetRequest(
        @NotNull Boolean confirm,
        Boolean acceptPolicyException,
        @NotNull @Valid List<CommissionRowRequest> arrangements) {
}
