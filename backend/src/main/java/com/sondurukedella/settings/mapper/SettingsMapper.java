package com.sondurukedella.settings.mapper;

import com.sondurukedella.settings.dto.CommissionPolicyResponse;
import com.sondurukedella.settings.dto.CompanyProfileResponse;
import com.sondurukedella.settings.dto.ExpenseCategoryResponse;
import com.sondurukedella.settings.dto.LookupItemResponse;
import com.sondurukedella.settings.dto.RevenueCategoryResponse;
import com.sondurukedella.settings.dto.WageSettingsResponse;
import com.sondurukedella.settings.entity.CommissionPolicy;
import com.sondurukedella.settings.entity.CompanyProfile;
import com.sondurukedella.settings.entity.ExpenseCategory;
import com.sondurukedella.settings.entity.RevenueCategory;
import com.sondurukedella.settings.entity.SimpleLookup;
import com.sondurukedella.settings.entity.WageSettings;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class SettingsMapper {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public CompanyProfileResponse toResponse(CompanyProfile profile) {
        return new CompanyProfileResponse(
                profile.getName(),
                profile.getTagline(),
                profile.getAddress(),
                profile.getPhone(),
                profile.getEmail(),
                profile.getRegistrationNo(),
                profile.getOwnerName(),
                profile.getSupplierCreditDays(),
                profile.getFinancialYearStartMonth(),
                profile.getVersion());
    }

    public CommissionPolicyResponse toResponse(CommissionPolicy policy) {
        BigDecimal pool = policy.getDesignCommissionPoolPct();
        return new CommissionPolicyResponse(
                pool,
                HUNDRED.subtract(pool),
                policy.getConstructionWarningPct(),
                policy.getVersion());
    }

    public WageSettingsResponse toResponse(WageSettings wages) {
        return new WageSettingsResponse(
                wages.getDefaultDailyWage(),
                wages.getHoursPerDay(),
                wages.getOvertimeMultiplier(),
                wages.getPayday(),
                wages.getVersion());
    }

    public ExpenseCategoryResponse toResponse(ExpenseCategory category) {
        return new ExpenseCategoryResponse(
                category.getId(),
                category.getExpenseGroup(),
                category.getName(),
                category.isLocked(),
                category.isSystemGenerated(),
                category.isActive(),
                category.getSortOrder());
    }

    public LookupItemResponse toResponse(SimpleLookup item) {
        return new LookupItemResponse(item.getId(), item.getName(), item.isActive(), item.getSortOrder());
    }

    public RevenueCategoryResponse toResponse(RevenueCategory category) {
        return new RevenueCategoryResponse(category, category.displayName());
    }
}
