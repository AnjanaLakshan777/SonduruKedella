package com.sondurukedella.settings.controller;

import com.sondurukedella.settings.dto.CommissionPolicyResponse;
import com.sondurukedella.settings.dto.CommissionPolicyUpdateRequest;
import com.sondurukedella.settings.dto.CompanyProfileResponse;
import com.sondurukedella.settings.dto.CompanyProfileUpdateRequest;
import com.sondurukedella.settings.dto.RevenueCategoryResponse;
import com.sondurukedella.settings.dto.SettingsOverviewResponse;
import com.sondurukedella.settings.dto.WageSettingsResponse;
import com.sondurukedella.settings.dto.WageSettingsUpdateRequest;
import com.sondurukedella.settings.service.CommissionPolicyService;
import com.sondurukedella.settings.service.CompanyProfileService;
import com.sondurukedella.settings.service.ExpenseCategoryService;
import com.sondurukedella.settings.service.MeasurementUnitService;
import com.sondurukedella.settings.service.PaymentMethodService;
import com.sondurukedella.settings.service.RevenueCategoryService;
import com.sondurukedella.settings.service.WageSettingsService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final CompanyProfileService companyProfileService;
    private final CommissionPolicyService commissionPolicyService;
    private final WageSettingsService wageSettingsService;
    private final RevenueCategoryService revenueCategoryService;
    private final ExpenseCategoryService expenseCategoryService;
    private final MeasurementUnitService measurementUnitService;
    private final PaymentMethodService paymentMethodService;

    @GetMapping
    public SettingsOverviewResponse overview() {
        return new SettingsOverviewResponse(
                companyProfileService.get(),
                commissionPolicyService.get(),
                wageSettingsService.get(),
                revenueCategoryService.list(),
                expenseCategoryService.listActive(null),
                measurementUnitService.listActive(),
                paymentMethodService.listActive());
    }

    @GetMapping("/company")
    public CompanyProfileResponse company() {
        return companyProfileService.get();
    }

    @PutMapping("/company")
    public CompanyProfileResponse updateCompany(@Valid @RequestBody CompanyProfileUpdateRequest request) {
        return companyProfileService.update(request);
    }

    @GetMapping("/commission-policy")
    public CommissionPolicyResponse commissionPolicy() {
        return commissionPolicyService.get();
    }

    @PutMapping("/commission-policy")
    public CommissionPolicyResponse updateCommissionPolicy(@Valid @RequestBody CommissionPolicyUpdateRequest request) {
        return commissionPolicyService.update(request);
    }

    @GetMapping("/wages")
    public WageSettingsResponse wages() {
        return wageSettingsService.get();
    }

    @PutMapping("/wages")
    public WageSettingsResponse updateWages(@Valid @RequestBody WageSettingsUpdateRequest request) {
        return wageSettingsService.update(request);
    }

    /** Read-only by design: there are no endpoints to add, rename or remove revenue categories. */
    @GetMapping("/revenue-categories")
    public List<RevenueCategoryResponse> revenueCategories() {
        return revenueCategoryService.list();
    }
}
