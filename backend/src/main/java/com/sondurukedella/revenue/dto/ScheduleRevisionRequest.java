package com.sondurukedella.revenue.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// The full scheduled plan (no variations). Send existing rows with id + version. Billed rows must stay
// the same, unbilled ones can change or be dropped, rows without an id are new. Must still add up to
// the contract value.
public record ScheduleRevisionRequest(@NotEmpty @Valid List<Row> installments) {

    public record Row(Long id, Long version, @NotNull @Valid InstallmentDraft installment) {
    }
}
