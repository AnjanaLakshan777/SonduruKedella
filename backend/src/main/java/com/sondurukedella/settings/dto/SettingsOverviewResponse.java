package com.sondurukedella.settings.dto;

import java.util.List;

/** Everything the Settings page loads at once; top-level names follow the frontend {@code Settings} type. */
public record SettingsOverviewResponse(
        CompanyProfileResponse company,
        CommissionPolicyResponse commission,
        WageSettingsResponse wages,
        List<RevenueCategoryResponse> revenueCategories,
        List<ExpenseCategoryResponse> expenseCategories,
        List<LookupItemResponse> units,
        List<LookupItemResponse> paymentMethods) {
}
