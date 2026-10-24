package com.sondurukedella.revenue.dto;

import jakarta.validation.constraints.NotNull;

// reason is optional (the UI's "Reverse to review again" doesn't ask for one) but goes in the activity log when given
public record DeductionReverseRequest(String reason, @NotNull Long version) {
}
