package com.sondurukedella.settings.dto;

import com.sondurukedella.settings.entity.RevenueCategory;

/** A fixed revenue category with its display name. */
public record RevenueCategoryResponse(RevenueCategory code, String name) {
}
