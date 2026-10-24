package com.sondurukedella.projects.dto;

import com.sondurukedella.clients.dto.ClientCreateRequest;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;
import com.sondurukedella.revenue.dto.DelayTermsDraft;
import com.sondurukedella.revenue.dto.InstallmentDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Everything the Create Project page saves in one go. Send either clientId or newClient.
// There's no code field: the server assigns it, the one the page shows is only a preview.
// status defaults to PLANNING, same as the column default.
public record ProjectCreateRequest(
        @NotBlank(message = "Enter a project name.") @Size(max = 200) String name,
        @NotNull ProjectType type,
        Long clientId,
        @Valid ClientCreateRequest newClient,
        @Size(max = 200) String location,
        String description,
        @NotNull(message = "Enter the agreed contract value.") @DecimalMin("0") @Digits(integer = 13, fraction = 2)
        BigDecimal contractValue,
        @NotNull(message = "Choose the start date.") LocalDate startDate,
        @NotNull(message = "Choose the expected completion date.") LocalDate expectedCompletion,
        ProjectStatus status,
        String notes,
        List<@NotNull @Valid InstallmentDraft> schedule,
        @Valid DelayTermsDraft delayTerms) {
}
