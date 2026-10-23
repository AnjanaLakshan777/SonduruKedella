package com.sondurukedella.settings.dto;

import com.sondurukedella.settings.entity.ExpenseGroup;

public record ExpenseCategoryResponse(
        long id,
        ExpenseGroup group,
        String name,
        boolean locked,
        boolean systemGenerated,
        boolean active,
        int sortOrder) {
}
