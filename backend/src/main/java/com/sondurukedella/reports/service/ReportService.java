package com.sondurukedella.reports.service;

import com.sondurukedella.commissions.dto.CommissionResponse;
import com.sondurukedella.commissions.dto.CommissionState;
import com.sondurukedella.commissions.dto.CommissionSummaryResponse;
import com.sondurukedella.commissions.dto.CommissionTotals;
import com.sondurukedella.commissions.dto.ProjectCommissionsResponse;
import com.sondurukedella.commissions.service.CommissionService;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.expenses.dto.ExpenseFilter;
import com.sondurukedella.expenses.dto.ExpenseSummaryResponse;
import com.sondurukedella.expenses.entity.ExpenseScope;
import com.sondurukedella.expenses.service.ExpenseService;
import com.sondurukedella.inventory.dto.InventoryValuationResponse;
import com.sondurukedella.inventory.dto.MaterialResponse;
import com.sondurukedella.inventory.dto.ProjectMaterialUsageResponse;
import com.sondurukedella.inventory.service.MaterialService;
import com.sondurukedella.inventory.service.StockMovementService;
import com.sondurukedella.payroll.dto.PayrollWeekSummary;
import com.sondurukedella.payroll.dto.ProjectLabourResponse;
import com.sondurukedella.payroll.service.PayrollService;
import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;
import com.sondurukedella.projects.service.ProjectService;
import com.sondurukedella.reports.dto.DashboardReport;
import com.sondurukedella.reports.dto.FinancialSummaryReport;
import com.sondurukedella.reports.dto.InventoryValuationReport;
import com.sondurukedella.reports.dto.PayrollReport;
import com.sondurukedella.reports.dto.ProjectCostBreakdown;
import com.sondurukedella.reports.dto.ProjectCostBreakdown.Category;
import com.sondurukedella.reports.dto.ProjectCostBreakdown.CostCategory;
import com.sondurukedella.reports.dto.ProjectCostBreakdown.Line;
import com.sondurukedella.reports.dto.ProjectFinancialReport;
import com.sondurukedella.reports.dto.ProjectFinancialReport.ProfitBasis;
import com.sondurukedella.reports.dto.ProjectRefDto;
import com.sondurukedella.reports.dto.ReceivablesReport;
import com.sondurukedella.reports.dto.ReceivablesReport.AgingBucket;
import com.sondurukedella.reports.repository.ReportQueries;
import com.sondurukedella.revenue.dto.InstallmentReceivableResponse;
import com.sondurukedella.revenue.dto.ReceivableStatus;
import com.sondurukedella.revenue.dto.ReceivableTotals;
import com.sondurukedella.revenue.dto.ReceivablesSummaryResponse;
import com.sondurukedella.revenue.service.ReceivableService;
import com.sondurukedella.settings.service.CompanyProfileService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// Read-only reports. Nothing here writes: figures with rules come from the owning modules' services
// (receivables, FIFO usage, live payroll, commission state), and plain date-ranged sums come from
// ReportQueries. Each report runs in one REPEATABLE READ read-only transaction, so all of its figures
// come from the same database snapshot even while other users are posting.
//
// Profit rule (DATABASE.md "Profitability", the frontend's finance.ts): revenue recorded − written off −
// project costs, where project costs = manual project expenses + FIFO materials issued less returns +
// labour + confirmed commission. Company expenses are not project costs.
@Service
@RequiredArgsConstructor
public class ReportService {

    public static final int MAX_RANGE_DAYS = 366;
    public static final int MAX_PAGE_SIZE = 100;
    public static final Set<String> RECEIVABLE_SORTS = Set.of("daysOverdue", "dueDate", "outstanding", "billedOn", "project");

    // supplier invoices and other income are in the schema but not built, so they can't be counted yet
    static final List<String> NOT_BUILT = List.of(
            "Supplier invoices (PROJECT invoices as material cost, payables) are not implemented yet.",
            "Other income is not implemented yet, so it is not in revenue.");

    private final ProjectService projectService;
    private final ReceivableService receivableService;
    private final ExpenseService expenseService;
    private final StockMovementService stockService;
    private final MaterialService materialService;
    private final PayrollService payrollService;
    private final CommissionService commissionService;
    private final CompanyProfileService companyProfile;
    private final ReportQueries queries;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectFinancialReport projectFinancial(long projectId) {
        ProjectResponse project = projectService.get(projectId);
        return financial(project);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectCostBreakdown projectCostBreakdown(long projectId) {
        ProjectResponse project = projectService.get(projectId);
        List<Category> categories = new ArrayList<>();

        List<Line> expenseLines = queries.projectExpensesByCategory(projectId).stream()
                .map(c -> new Line(c.category(), c.amount(), c.count())).toList();
        categories.add(category(CostCategory.MANUAL_EXPENSES, expenseLines));

        ProjectMaterialUsageResponse usage = stockService.projectUsage(projectId);
        categories.add(category(CostCategory.MATERIALS, usage.materials().stream()
                .map(m -> new Line(m.name() + " (" + m.sku() + ")", m.netCost(), null)).toList()));

        ProjectLabourResponse labour = payrollService.projectLabour(projectId);
        List<Line> labourLines = new ArrayList<>();
        labourLines.add(new Line("Paid weeks (frozen split)", Money.amount(labour.paidWeeks()), null));
        labourLines.add(new Line("Corrections to paid weeks", Money.amount(labour.corrections()), null));
        labourLines.add(new Line("Open weeks (live, can still change)", Money.amount(labour.openWeeks()), null));
        categories.add(category(CostCategory.LABOUR, labourLines));

        List<Line> commissionLines = commissionService.forProject(projectId).arrangements().stream()
                .filter(ReportService::isCommissionCost)
                .map(c -> new Line(c.recipient() + " (" + c.role() + ", " + c.ratePct().toPlainString() + "%)", c.amount(), 1L))
                .toList();
        categories.add(category(CostCategory.COMMISSION, commissionLines));

        BigDecimal total = Money.amount(categories.stream().map(Category::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        List<String> excluded = new ArrayList<>(List.of(
                "Company-level expenses (not a project cost).",
                "Stock purchases (stock value until issued; only issues less returns are project cost).",
                "Payroll advances and payroll payments (cash, not cost; labour is gross wages).",
                "Commission payments, drafts and voided arrangements (only confirmed arrangements are cost).",
                "Stock adjustments (they change stock value, not a project's cost)."));
        excluded.addAll(NOT_BUILT.subList(0, 1));
        return new ProjectCostBreakdown(today(), ref(project), categories, total, excluded);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardReport dashboard() {
        LocalDate today = today();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        Map<String, Long> activeByType = new LinkedHashMap<>();
        for (ProjectStatus s : ProjectStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        for (ProjectType t : ProjectType.values()) {
            activeByType.put(t.name(), 0L);
        }
        long active = 0;
        BigDecimal activeContract = BigDecimal.ZERO;
        for (ReportQueries.StatusCount c : queries.projectCounts()) {
            byStatus.merge(c.status(), c.count(), Long::sum);
            if (isActive(c.status())) {
                active += c.count();
                activeByType.merge(c.type(), c.count(), Long::sum);
                activeContract = activeContract.add(c.contractValue());
            }
        }

        ReportQueries.Billing billing = queries.billing(null, null);
        ReceivablesSummaryResponse receivables = receivableService.summary();

        // every project's own report, summed, so the dashboard and the project pages always agree
        BigDecimal projectCosts = BigDecimal.ZERO;
        BigDecimal projectProfit = BigDecimal.ZERO;
        for (ReportQueries.ProjectRef p : queries.allProjects()) {
            ProjectFinancialReport report = financial(projectService.get(p.id()));
            projectCosts = projectCosts.add(report.costs().total());
            projectProfit = projectProfit.add(report.profit().profit());
        }
        ExpenseSummaryResponse expenses = expenseService.summary(allExpenses(null, null));
        CommissionSummaryResponse commissions = commissionService.summary(null);

        LocalDate fyStart = financialYearStart(today);
        Map<LocalDate, BigDecimal> revenue = byMonth(queries.revenueByMonth(fyStart, today));
        Map<LocalDate, BigDecimal> received = byMonth(queries.receiptsByMonth(fyStart, today));
        Map<LocalDate, BigDecimal> spent = byMonth(queries.expensesByMonth(fyStart, today));
        List<DashboardReport.Month> months = new ArrayList<>();
        for (LocalDate m = fyStart; !m.isAfter(today); m = m.plusMonths(1)) {
            months.add(new DashboardReport.Month(m, revenue.getOrDefault(m, zero()), received.getOrDefault(m, zero()),
                    spent.getOrDefault(m, zero())));
        }

        return new DashboardReport(today,
                new DashboardReport.Projects(active, activeByType, byStatus, Money.amount(activeContract)),
                new DashboardReport.Revenue(billing.net(), queries.receipts(null, null).amount(), queries.writeOffs(null, null).amount()),
                new DashboardReport.Receivables(receivables.outstanding(), receivables.overdue(), receivables.openInstallments(),
                        receivables.overdueInstallments()),
                new DashboardReport.ProjectResults(Money.amount(projectCosts), Money.amount(projectProfit), expenses.companyTotal()),
                new DashboardReport.Payables(expenses.outstanding(), commissions.outstanding()),
                materialService.valuation().stockValue(),
                fyStart, months, NOT_BUILT);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FinancialSummaryReport financialSummary(LocalDate from, LocalDate to) {
        requireRange(from, to);
        ReportQueries.Billing billing = queries.billing(from, to);
        ReportQueries.Total receipts = queries.receipts(from, to);
        ExpenseSummaryResponse expenses = expenseService.summary(allExpenses(from, to));
        ReportQueries.MaterialUse materials = queries.materialUse(from, to);
        ReportQueries.Total confirmed = queries.commissionConfirmed(from, to);

        ReceivablesSummaryResponse receivables = receivableService.summary();
        ExpenseSummaryResponse allTime = expenseService.summary(allExpenses(null, null));
        CommissionSummaryResponse commissions = commissionService.summary(null);
        InventoryValuationResponse stock = materialService.valuation();

        return new FinancialSummaryReport(from, to,
                new FinancialSummaryReport.Revenue(billing.installments(), billing.billed(), billing.deductions(), billing.net(),
                        receipts.count(), receipts.amount(), queries.writeOffs(from, to).amount()),
                new FinancialSummaryReport.Expenses(expenses.count(), expenses.projectTotal(), expenses.companyTotal(), expenses.total()),
                new FinancialSummaryReport.Materials(materials.issued(), materials.returned(), materials.net()),
                labour(payrollWeeks(from, to)),
                new FinancialSummaryReport.Commission(confirmed.count(), confirmed.amount(), queries.commissionPaid(from, to).amount()),
                new FinancialSummaryReport.Position(today(), receivables.outstanding(), receivables.overdue(), allTime.outstanding(),
                        commissions.outstanding(), stock.stockValue()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PayrollReport payroll(LocalDate from, LocalDate to) {
        requireRange(from, to);
        List<PayrollWeekSummary> weeks = payrollWeeks(from, to);
        return new PayrollReport(labour(weeks), weeks);
    }

    // Billed installments only (unbilled ones are not receivables). openOnly (default) drops settled ones.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReceivablesReport receivables(Long projectId, ReceivableStatus status, AgingBucket aging, Boolean openOnly, Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("The page size can be at most " + MAX_PAGE_SIZE + ".");
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!RECEIVABLE_SORTS.contains(order.getProperty())) {
                throw new InvalidRequestException("Receivables cannot be sorted by \"" + order.getProperty()
                        + "\". Use one of: daysOverdue, dueDate, outstanding, billedOn, project.");
            }
        }
        if (status == ReceivableStatus.UNBILLED) {
            throw new InvalidRequestException("Unbilled installments are not receivables.");
        }
        if (projectId != null) {
            projectService.requireExists(projectId);
        }
        boolean open = openOnly == null || openOnly;
        List<InstallmentReceivableResponse> installments = receivableService.billedReceivables().stream()
                .filter(r -> projectId == null || r.projectId() == projectId)
                .filter(r -> status == null || r.status() == status)
                .filter(r -> !open || r.outstanding().signum() > 0)
                .toList();
        Map<Long, ReportQueries.ProjectRef> projects = queries.projects(installments.stream().map(InstallmentReceivableResponse::projectId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(ReportQueries.ProjectRef::id, Function.identity()));
        List<ReceivablesReport.Row> rows = installments.stream().map(r -> row(r, projects.get(r.projectId())))
                .filter(r -> aging == null || r.aging() == aging)
                .sorted(receivableOrder(pageable.getSort()))
                .toList();

        Map<AgingBucket, BigDecimal> byAging = new EnumMap<>(AgingBucket.class);
        for (AgingBucket b : AgingBucket.values()) {
            byAging.put(b, zero());
        }
        rows.forEach(r -> byAging.merge(r.aging(), r.outstanding(), BigDecimal::add));
        ReceivablesReport.Totals totals = new ReceivablesReport.Totals(rows.size(), sum(rows, ReceivablesReport.Row::net),
                sum(rows, ReceivablesReport.Row::received), sum(rows, ReceivablesReport.Row::writtenOff),
                sum(rows, ReceivablesReport.Row::outstanding),
                sum(rows.stream().filter(r -> r.status() == ReceivableStatus.OVERDUE).toList(), ReceivablesReport.Row::outstanding),
                byAging);
        return new ReceivablesReport(today(), totals, page(rows, pageable));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public InventoryValuationReport inventoryValuation(String query, String category, Pageable pageable) {
        InventoryValuationResponse totals = materialService.valuation();
        return new InventoryValuationReport(today(), totals.materials(), totals.materialsInStock(), totals.stockValue(),
                materialService.search(query, category, null, pageable));
    }

    private ProjectFinancialReport financial(ProjectResponse project) {
        long id = project.id();
        ReceivableTotals revenue = receivableService.forProject(id).totals();
        BigDecimal manual = expenseService.summary(new ExpenseFilter(null, ExpenseScope.PROJECT, id, null, null, null, null, null, null))
                .projectTotal();
        BigDecimal materials = stockService.projectUsage(id).netCost();
        ProjectLabourResponse labour = payrollService.projectLabour(id);
        ProjectCommissionsResponse commissions = commissionService.forProject(id);
        CommissionTotals commission = commissions.totals();

        BigDecimal labourTotal = Money.amount(labour.total());
        BigDecimal total = Money.amount(manual.add(materials).add(labourTotal).add(commission.committed()));
        BigDecimal profit = Money.amount(revenue.revenueRecognised().subtract(revenue.writtenOff()).subtract(total));
        BigDecimal margin = revenue.revenueRecognised().signum() > 0
                ? profit.multiply(BigDecimal.valueOf(100)).divide(revenue.revenueRecognised(), 2, RoundingMode.HALF_UP)
                : null;
        ProfitBasis basis = project.status() == ProjectStatus.COMPLETED ? ProfitBasis.RECORDED : ProfitBasis.ESTIMATED;

        return new ProjectFinancialReport(today(), ref(project), project.contractValue(),
                new ProjectFinancialReport.Revenue(revenue.billed(), revenue.deductions(), revenue.revenueRecognised(), revenue.received(),
                        revenue.writtenOff(), revenue.outstanding(), revenue.overdue(), revenue.unbilled()),
                new ProjectFinancialReport.Costs(Money.amount(manual), Money.amount(materials), labourTotal,
                        Money.amount(labour.openWeeks()), commission.committed(), total),
                new ProjectFinancialReport.Commission(commission.committed(), commission.paid(), commission.outstanding(), commission.pendingAmount()),
                new ProjectFinancialReport.Profit(profit, margin, basis),
                NOT_BUILT);
    }

    // Mondays in [from, to], newest first, through the payroll module's own week summaries.
    private List<PayrollWeekSummary> payrollWeeks(LocalDate from, LocalDate to) {
        LocalDate first = from.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate last = to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (first.isAfter(last)) {
            return List.of();
        }
        return payrollService.weeks(first, last);
    }

    private static FinancialSummaryReport.Labour labour(List<PayrollWeekSummary> weeks) {
        BigDecimal gross = sumWeeks(weeks, PayrollWeekSummary::grossPay);
        BigDecimal corrections = sumWeeks(weeks, PayrollWeekSummary::corrections);
        return new FinancialSummaryReport.Labour(
                weeks.isEmpty() ? null : weeks.getLast().weekStart(),
                weeks.isEmpty() ? null : weeks.getFirst().weekStart(),
                weeks.size(), gross, corrections, Money.amount(gross.add(corrections)),
                sumWeeks(weeks, PayrollWeekSummary::paid),
                sumWeeks(weeks.stream().filter(PayrollWeekSummary::payable).toList(), PayrollWeekSummary::remaining),
                sumWeeks(weeks.stream().filter(w -> !w.payable()).toList(), PayrollWeekSummary::remaining));
    }

    private ReceivablesReport.Row row(InstallmentReceivableResponse r, ReportQueries.ProjectRef project) {
        return new ReceivablesReport.Row(r.id(), r.projectId(), project.code(), project.name(), project.clientId(), project.clientName(),
                r.description(), r.billedOn(), r.dueDate(), r.amount(), r.deduction(), r.net(), r.received(), r.writtenOff(),
                r.outstanding(), r.status(), r.daysOverdue(), aging(r));
    }

    static AgingBucket aging(InstallmentReceivableResponse r) {
        if (r.outstanding().signum() == 0) {
            return AgingBucket.SETTLED;
        }
        if (r.status() != ReceivableStatus.OVERDUE) {
            return AgingBucket.NOT_DUE;
        }
        long days = r.daysOverdue();
        return days <= 30 ? AgingBucket.DAYS_1_30 : days <= 60 ? AgingBucket.DAYS_31_60 : days <= 90 ? AgingBucket.DAYS_61_90
                : AgingBucket.DAYS_OVER_90;
    }

    // most overdue first by default; installment id breaks ties so pages are stable
    private static Comparator<ReceivablesReport.Row> receivableOrder(Sort sort) {
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Order.desc("daysOverdue"), Sort.Order.asc("dueDate"));
        }
        Comparator<ReceivablesReport.Row> result = null;
        for (Sort.Order order : sort) {
            Comparator<ReceivablesReport.Row> next = switch (order.getProperty()) {
                case "daysOverdue" -> Comparator.comparingLong(ReceivablesReport.Row::daysOverdue);
                case "dueDate" -> Comparator.comparing(ReceivablesReport.Row::dueDate);
                case "outstanding" -> Comparator.comparing(ReceivablesReport.Row::outstanding);
                case "billedOn" -> Comparator.comparing(ReceivablesReport.Row::billedOn);
                default -> Comparator.comparing(ReceivablesReport.Row::projectCode);
            };
            result = result == null ? (order.isDescending() ? next.reversed() : next)
                    : result.thenComparing(order.isDescending() ? next.reversed() : next);
        }
        return result.thenComparingLong(ReceivablesReport.Row::installmentId);
    }

    private static <T> PageResponse<T> page(List<T> rows, Pageable pageable) {
        int size = pageable.getPageSize();
        int from = (int) Math.min((long) pageable.getPageNumber() * size, rows.size());
        int to = Math.min(from + size, rows.size());
        return new PageResponse<>(rows.subList(from, to), pageable.getPageNumber(), size, rows.size(), (rows.size() + size - 1) / size);
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new InvalidRequestException("Give both from and to (yyyy-MM-dd).");
        }
        if (from.isAfter(to)) {
            throw new InvalidRequestException("from must be on or before to.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new InvalidRequestException("Ask for at most " + MAX_RANGE_DAYS + " days at a time.");
        }
    }

    private LocalDate financialYearStart(LocalDate today) {
        int month = companyProfile.get().financialYearStartMonth();
        LocalDate start = today.withMonth(month).withDayOfMonth(1);
        return start.isAfter(today) ? start.minusYears(1) : start;
    }

    private static ExpenseFilter allExpenses(LocalDate from, LocalDate to) {
        return new ExpenseFilter(null, null, null, null, null, null, null, from, to);
    }

    private static Category category(CostCategory category, List<Line> lines) {
        return new Category(category, Money.amount(lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add)), lines);
    }

    private static boolean isCommissionCost(CommissionResponse c) {
        return c.status() == CommissionState.CONFIRMED || c.status() == CommissionState.PARTIALLY_PAID || c.status() == CommissionState.PAID;
    }

    private static boolean isActive(String status) {
        return ProjectStatus.IN_PROGRESS.name().equals(status) || ProjectStatus.ON_HOLD.name().equals(status);
    }

    private static ProjectRefDto ref(ProjectResponse p) {
        return new ProjectRefDto(p.id(), p.code(), p.name(), p.type(), p.status(), p.clientId(), p.clientName());
    }

    private static Map<LocalDate, BigDecimal> byMonth(List<ReportQueries.MonthAmount> rows) {
        return rows.stream().collect(Collectors.toMap(ReportQueries.MonthAmount::month, ReportQueries.MonthAmount::amount,
                BigDecimal::add, TreeMap::new));
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> field) {
        return Money.amount(rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal sumWeeks(List<PayrollWeekSummary> weeks, Function<PayrollWeekSummary, BigDecimal> field) {
        return sum(weeks, field);
    }

    private static BigDecimal zero() {
        return Money.amount(BigDecimal.ZERO);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
