package com.sondurukedella.projects.dto;

import com.sondurukedella.projects.entity.ProjectStatus;
import jakarta.validation.constraints.NotNull;

// Send the version you loaded; if someone else changed the project since, you get a 409.
public record ProjectStatusUpdateRequest(
        @NotNull ProjectStatus status,
        @NotNull Long version) {
}
