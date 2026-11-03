package com.sondurukedella.security.access;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

// The app's pages (the sidebar): the permissions ticked for each Admin or "Other" user.
// Each page lists the data it has to read to draw itself and the data its buttons change.
// Dashboard, Projects and Reports add up figures from everything, so they read everything.
// Users stays Super Admin only.
// Keep the names in sync with ck_app_user_pages_page.
public enum AppPage {
    DASHBOARD(EnumSet.allOf(ApiArea.class), EnumSet.noneOf(ApiArea.class)),
    PROJECTS(EnumSet.allOf(ApiArea.class), EnumSet.of(ApiArea.PROJECTS, ApiArea.PROJECT_PAYMENTS, ApiArea.EXPENSES, ApiArea.SUPPLIERS,
            ApiArea.COMMISSIONS)),
    CLIENTS(EnumSet.of(ApiArea.CLIENTS, ApiArea.PROJECTS, ApiArea.PROJECT_PAYMENTS, ApiArea.RECEIVABLES),
            EnumSet.of(ApiArea.CLIENTS, ApiArea.PROJECT_PAYMENTS)),
    REVENUE(EnumSet.of(ApiArea.CLIENTS, ApiArea.PROJECTS, ApiArea.PROJECT_PAYMENTS, ApiArea.RECEIVABLES, ApiArea.OTHER_INCOME),
            EnumSet.of(ApiArea.PROJECT_PAYMENTS, ApiArea.OTHER_INCOME)),
    EXPENSES(EnumSet.of(ApiArea.EXPENSES, ApiArea.PROJECTS, ApiArea.CLIENTS, ApiArea.SUPPLIERS),
            EnumSet.of(ApiArea.EXPENSES, ApiArea.SUPPLIERS)),
    COMMISSIONS(EnumSet.of(ApiArea.COMMISSIONS, ApiArea.PROJECTS, ApiArea.PROJECT_PAYMENTS, ApiArea.CLIENTS),
            EnumSet.of(ApiArea.COMMISSIONS)),
    REPORTS(EnumSet.allOf(ApiArea.class), EnumSet.noneOf(ApiArea.class)),
    SUPPLIERS(EnumSet.of(ApiArea.SUPPLIERS, ApiArea.SUPPLIER_INVOICES, ApiArea.INVENTORY, ApiArea.PROJECTS),
            EnumSet.of(ApiArea.SUPPLIERS, ApiArea.SUPPLIER_INVOICES, ApiArea.INVENTORY)),
    INVENTORY(EnumSet.of(ApiArea.INVENTORY, ApiArea.PROJECTS, ApiArea.SUPPLIERS, ApiArea.SUPPLIER_INVOICES),
            EnumSet.of(ApiArea.INVENTORY)),
    LABOUR(EnumSet.of(ApiArea.LABOUR, ApiArea.PAYROLL, ApiArea.PROJECTS), EnumSet.of(ApiArea.LABOUR, ApiArea.PAYROLL)),
    SETTINGS(EnumSet.of(ApiArea.SETTINGS), EnumSet.of(ApiArea.SETTINGS));

    private final Set<ApiArea> reads;
    private final Set<ApiArea> writes;

    AppPage(Set<ApiArea> reads, Set<ApiArea> writes) {
        this.reads = reads;
        this.writes = writes;
    }

    // every signed-in user reads settings (company name, units, categories)
    public static Set<ApiArea> readable(Collection<AppPage> pages) {
        Set<ApiArea> areas = EnumSet.of(ApiArea.SETTINGS);
        pages.forEach(p -> areas.addAll(p.reads));
        return areas;
    }

    public static Set<ApiArea> writable(Collection<AppPage> pages) {
        Set<ApiArea> areas = EnumSet.noneOf(ApiArea.class);
        pages.forEach(p -> areas.addAll(p.writes));
        return areas;
    }
}
