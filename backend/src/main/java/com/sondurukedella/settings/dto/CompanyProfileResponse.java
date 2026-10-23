package com.sondurukedella.settings.dto;

/** Company information; field names follow the frontend {@code Settings.company} contract. */
public record CompanyProfileResponse(
        String name,
        String tagline,
        String address,
        String phone,
        String email,
        String registration,
        String owner,
        int supplierCreditDays,
        int financialYearStartMonth,
        long version) {
}
