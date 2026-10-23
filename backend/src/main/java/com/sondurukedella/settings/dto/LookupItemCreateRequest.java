package com.sondurukedella.settings.dto;

import jakarta.validation.constraints.NotBlank;

/** The maximum length depends on the list and is checked by the service. */
public record LookupItemCreateRequest(@NotBlank String name) {
}
