package com.sondurukedella.reports.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sondurukedella.reports.dto.ReceivablesReport.AgingBucket;
import com.sondurukedella.revenue.dto.InstallmentReceivableResponse;
import com.sondurukedella.revenue.dto.ReceivableStatus;
import com.sondurukedella.revenue.entity.InstallmentKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReceivableAgingTest {

    private static InstallmentReceivableResponse row(String outstanding, ReceivableStatus status, long daysOverdue) {
        return new InstallmentReceivableResponse(1, 1, "x", LocalDate.of(2026, 1, 1), new BigDecimal("1000"), InstallmentKind.SCHEDULED,
                true, LocalDate.of(2026, 1, 1), BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(outstanding), status, daysOverdue, 0);
    }

    @Test
    void bucketsFollowTheDaysOverdueTheRevenueModuleWorkedOut() {
        assertThat(ReportService.aging(row("0.00", ReceivableStatus.PAID, 0))).isEqualTo(AgingBucket.SETTLED);
        assertThat(ReportService.aging(row("10.00", ReceivableStatus.BILLED, 0))).isEqualTo(AgingBucket.NOT_DUE);
        assertThat(ReportService.aging(row("10.00", ReceivableStatus.PARTIALLY_PAID, 0))).isEqualTo(AgingBucket.NOT_DUE);
        assertThat(ReportService.aging(row("10.00", ReceivableStatus.OVERDUE, 1))).isEqualTo(AgingBucket.DAYS_1_30);
        assertThat(ReportService.aging(row("10.00", ReceivableStatus.OVERDUE, 30))).isEqualTo(AgingBucket.DAYS_1_30);
        assertThat(ReportService.aging(row("10.00", ReceivableStatus.OVERDUE, 31))).isEqualTo(AgingBucket.DAYS_31_60);
        assertThat(ReportService.aging(row("10.00", ReceivableStatus.OVERDUE, 60))).isEqualTo(AgingBucket.DAYS_31_60);
        assertThat(ReportService.aging(row("10.00", ReceivableStatus.OVERDUE, 61))).isEqualTo(AgingBucket.DAYS_61_90);
        assertThat(ReportService.aging(row("10.00", ReceivableStatus.OVERDUE, 90))).isEqualTo(AgingBucket.DAYS_61_90);
        assertThat(ReportService.aging(row("10.00", ReceivableStatus.OVERDUE, 91))).isEqualTo(AgingBucket.DAYS_OVER_90);
    }
}
