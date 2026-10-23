package com.sondurukedella.settings.dto;

import com.sondurukedella.settings.entity.ExpenseCategory;
import com.sondurukedella.settings.entity.ExpenseGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExpenseCategoryCreateRequest(
        @NotNull ExpenseGroup group,
        @NotBlank @Size(max = ExpenseCategory.NAME_MAX_LENGTH) String name) {
}
