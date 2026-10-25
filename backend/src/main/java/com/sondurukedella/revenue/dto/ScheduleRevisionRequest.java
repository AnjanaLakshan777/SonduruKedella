package com.sondurukedella.revenue.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// The whole scheduled part of the plan (variations are not part of it). Existing rows carry id and
// version; billed ones must be sent unchanged; unbilled ones may change or be left out (removed); rows
// without an id are new. The scheduled rows must still add up to the contract value.
public record ScheduleRevisionRequest(@NotEmpty @Valid List<Row> installments) {

    public record Row(Long id, Long version, @NotNull @Valid InstallmentDraft installment) {
    }
}
