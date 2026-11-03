package com.sondurukedella.security.access;

import java.util.Map;
import java.util.Optional;

// Groups of /api/v1 endpoints. A user's pages decide which areas they may read and change.
// Unknown paths have no area, and only a Super Admin gets through there.
public enum ApiArea {
    SETTINGS,
    CLIENTS,
    // the project itself: list, create, status, agreement
    PROJECTS,
    // money on a project: schedule, billing, variations, receipts, write-offs, delay terms
    PROJECT_PAYMENTS,
    RECEIVABLES,
    OTHER_INCOME,
    EXPENSES,
    SUPPLIERS,
    SUPPLIER_INVOICES,
    INVENTORY,
    // employees, attendance, overtime, holidays
    LABOUR,
    PAYROLL,
    COMMISSIONS,
    REPORTS,
    ACTIVITY;

    private static final String API = "/api/v1/";

    private static final Map<String, ApiArea> BY_FIRST_SEGMENT = Map.ofEntries(
            Map.entry("settings", SETTINGS),
            Map.entry("clients", CLIENTS),
            Map.entry("projects", PROJECTS),
            Map.entry("receivables", RECEIVABLES),
            Map.entry("other-income", OTHER_INCOME),
            Map.entry("expenses", EXPENSES),
            Map.entry("suppliers", SUPPLIERS),
            Map.entry("supplier-invoices", SUPPLIER_INVOICES),
            Map.entry("inventory", INVENTORY),
            Map.entry("employees", LABOUR),
            Map.entry("attendance", LABOUR),
            Map.entry("overtime", LABOUR),
            Map.entry("holidays", LABOUR),
            Map.entry("payroll", PAYROLL),
            Map.entry("commissions", COMMISSIONS),
            Map.entry("reports", REPORTS),
            Map.entry("activity", ACTIVITY));

    // /projects/{id}/<this> belongs to the payments area
    private static final Map<String, ApiArea> PROJECT_SUB_AREAS = Map.of(
            "schedule", PROJECT_PAYMENTS,
            "installments", PROJECT_PAYMENTS,
            "delay-terms", PROJECT_PAYMENTS,
            "variations", PROJECT_PAYMENTS,
            "receipts", PROJECT_PAYMENTS,
            "write-offs", PROJECT_PAYMENTS,
            "commissions", COMMISSIONS);

    // path is the request path without the context path, e.g. /api/v1/projects/7/receipts
    public static Optional<ApiArea> of(String path) {
        if (path == null || !path.startsWith(API)) {
            return Optional.empty();
        }
        String[] segments = path.substring(API.length()).split("/");
        ApiArea area = BY_FIRST_SEGMENT.get(segments[0]);
        if (area == PROJECTS && segments.length > 2) {
            return Optional.of(PROJECT_SUB_AREAS.getOrDefault(segments[2], PROJECTS));
        }
        return Optional.ofNullable(area);
    }
}
