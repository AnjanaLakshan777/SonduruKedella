package com.sondurukedella.security.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiAreaTest {

    @Test
    void pathsMapToTheirArea() {
        assertThat(ApiArea.of("/api/v1/projects")).contains(ApiArea.PROJECTS);
        assertThat(ApiArea.of("/api/v1/projects/7/agreement")).contains(ApiArea.PROJECTS);
        assertThat(ApiArea.of("/api/v1/projects/7/schedule")).contains(ApiArea.PROJECT_PAYMENTS);
        assertThat(ApiArea.of("/api/v1/projects/7/delay-terms/deduction")).contains(ApiArea.PROJECT_PAYMENTS);
        assertThat(ApiArea.of("/api/v1/projects/7/receipts/3/void")).contains(ApiArea.PROJECT_PAYMENTS);
        assertThat(ApiArea.of("/api/v1/projects/7/installments/2/bill")).contains(ApiArea.PROJECT_PAYMENTS);
        assertThat(ApiArea.of("/api/v1/projects/7/commissions")).contains(ApiArea.COMMISSIONS);
        assertThat(ApiArea.of("/api/v1/attendance/2026-11-03")).contains(ApiArea.LABOUR);
        assertThat(ApiArea.of("/api/v1/holidays")).contains(ApiArea.LABOUR);
        assertThat(ApiArea.of("/api/v1/reports/projects/7/financial")).contains(ApiArea.REPORTS);
        assertThat(ApiArea.of("/api/v1/settings/commission-policy")).contains(ApiArea.SETTINGS);
    }

    @Test
    void unknownPathsHaveNoArea() {
        assertThat(ApiArea.of("/api/v1/users")).isEmpty();
        assertThat(ApiArea.of("/api/v1/roles/1")).isEmpty();
        assertThat(ApiArea.of("/api/v1/Projects")).isEmpty();
        assertThat(ApiArea.of("/api/v1/")).isEmpty();
        assertThat(ApiArea.of("/somewhere/projects")).isEmpty();
        assertThat(ApiArea.of(null)).isEmpty();
    }

    @Test
    void everyoneReadsSettingsAndOnlyPagesWithButtonsWrite() {
        assertThat(AppPage.readable(List.of())).containsExactly(ApiArea.SETTINGS);
        assertThat(AppPage.writable(List.of(AppPage.REPORTS, AppPage.DASHBOARD))).isEmpty();
        assertThat(AppPage.readable(List.of(AppPage.REPORTS))).isEqualTo(EnumSet.allOf(ApiArea.class));
        assertThat(AppPage.writable(List.of(AppPage.LABOUR))).containsExactlyInAnyOrder(ApiArea.LABOUR, ApiArea.PAYROLL);
    }
}
