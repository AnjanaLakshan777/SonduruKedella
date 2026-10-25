package com.sondurukedella.reports.controller;

import com.sondurukedella.reports.dto.DashboardReport;
import com.sondurukedella.reports.dto.FinancialSummaryReport;
import com.sondurukedella.reports.dto.InventoryValuationReport;
import com.sondurukedella.reports.dto.PayrollReport;
import com.sondurukedella.reports.dto.ProjectCostBreakdown;
import com.sondurukedella.reports.dto.ProjectFinancialReport;
import com.sondurukedella.reports.dto.ReceivablesReport;
import com.sondurukedella.reports.dto.ReceivablesReport.AgingBucket;
import com.sondurukedella.reports.service.ReportService;
import com.sondurukedella.revenue.dto.ReceivableStatus;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Read-only reports: GET only, nothing here changes data. Both roles, through the central /api/v1 rule.
// Commission views already exist under /api/v1/commissions (summary, recipients, payments) and are
// not repeated here.
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;

    @GetMapping("/dashboard")
    public DashboardReport dashboard() {
        return service.dashboard();
    }

    @GetMapping("/financial-summary")
    public FinancialSummaryReport financialSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.financialSummary(from, to);
    }

    @GetMapping("/projects/{projectId}/financial")
    public ProjectFinancialReport projectFinancial(@PathVariable long projectId) {
        return service.projectFinancial(projectId);
    }

    @GetMapping("/projects/{projectId}/cost-breakdown")
    public ProjectCostBreakdown projectCostBreakdown(@PathVariable long projectId) {
        return service.projectCostBreakdown(projectId);
    }

    @GetMapping("/receivables")
    public ReceivablesReport receivables(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) ReceivableStatus status,
            @RequestParam(required = false) AgingBucket aging,
            @RequestParam(required = false) Boolean openOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.receivables(projectId, status, aging, openOnly, pageable);
    }

    @GetMapping("/inventory-valuation")
    public InventoryValuationReport inventoryValuation(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.inventoryValuation(q, category, pageable);
    }

    @GetMapping("/payroll")
    public PayrollReport payroll(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.payroll(from, to);
    }
}
