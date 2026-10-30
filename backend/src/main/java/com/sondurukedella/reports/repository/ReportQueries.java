package com.sondurukedella.reports.repository;

import com.sondurukedella.common.money.Money;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// Plain SQL sums for the reports. Only simple totals by date live here - anything with business
// rules goes through the owning module's service. from/to can be null (no limit).
@Repository
@RequiredArgsConstructor
public class ReportQueries {

    private static final String RANGE = " AND (CAST(:from AS date) IS NULL OR %1$s >= :from) AND (CAST(:to AS date) IS NULL OR %1$s <= :to)";

    private final NamedParameterJdbcTemplate jdbc;

    public record ProjectRef(long id, String code, String name, String type, String status, BigDecimal contractValue,
            long clientId, String clientName) {
    }

    public record StatusCount(String status, String type, long count, BigDecimal contractValue) {
    }

    // same per-installment rule as ReceivableCalculator: max(amount - deduction, 0)
    public record Billing(long installments, BigDecimal billed, BigDecimal deductions, BigDecimal net) {
    }

    public record Total(long count, BigDecimal amount) {
    }

    public record MaterialUse(BigDecimal issued, BigDecimal returned, BigDecimal net) {
    }

    public record MonthAmount(LocalDate month, BigDecimal amount) {
    }

    public record CategoryAmount(String category, long count, BigDecimal amount) {
    }

    public List<ProjectRef> projects(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query(PROJECTS + " WHERE p.id IN (:ids)", new MapSqlParameterSource("ids", ids), ReportQueries::projectRef);
    }

    public List<Long> projectIdsForClient(long clientId) {
        return jdbc.queryForList("SELECT id FROM projects WHERE client_id = :clientId ORDER BY id",
                new MapSqlParameterSource("clientId", clientId), Long.class);
    }

    public List<ProjectRef> allProjects() {
        return jdbc.query(PROJECTS + " ORDER BY p.id", new MapSqlParameterSource(), ReportQueries::projectRef);
    }

    public List<StatusCount> projectCounts() {
        return jdbc.query("SELECT status, project_type, count(*) AS n, coalesce(sum(contract_value), 0) AS value "
                + "FROM projects GROUP BY status, project_type", new MapSqlParameterSource(),
                (rs, i) -> new StatusCount(rs.getString("status"), rs.getString("project_type"), rs.getLong("n"), money(rs, "value")));
    }

    // by billed_on
    public Billing billing(LocalDate from, LocalDate to) {
        return jdbc.queryForObject("""
                SELECT count(*) AS n, coalesce(sum(i.amount), 0) AS billed,
                       coalesce(sum(coalesce(d.applied_amount, 0)), 0) AS deductions,
                       coalesce(sum(GREATEST(i.amount - coalesce(d.applied_amount, 0), 0)), 0) AS net
                  FROM installments i
                  LEFT JOIN delay_deduction_terms d ON d.applied_installment_id = i.id
                 WHERE i.billed_on IS NOT NULL""" + RANGE.formatted("i.billed_on"), range(from, to),
                (rs, i) -> new Billing(rs.getLong("n"), money(rs, "billed"), money(rs, "deductions"), money(rs, "net")));
    }

    // by payment_date, voided ones skipped
    public Total receipts(LocalDate from, LocalDate to) {
        return total("SELECT count(*) AS n, coalesce(sum(amount), 0) AS total FROM client_payments WHERE voided_at IS NULL"
                + RANGE.formatted("payment_date"), from, to);
    }

    // by payment_date, voided ones skipped
    public Total otherIncomeReceipts(LocalDate from, LocalDate to) {
        return total("SELECT count(*) AS n, coalesce(sum(amount), 0) AS total FROM other_income_receipts WHERE voided_at IS NULL"
                + RANGE.formatted("payment_date"), from, to);
    }

    // by write_off_date, reversed ones skipped
    public Total writeOffs(LocalDate from, LocalDate to) {
        return total("SELECT count(*) AS n, coalesce(sum(amount), 0) AS total FROM receivable_write_offs WHERE voided_at IS NULL"
                + RANGE.formatted("write_off_date"), from, to);
    }

    // issues are stored negative, returns positive, both at their FIFO cost (same as projectUsage)
    public MaterialUse materialUse(LocalDate from, LocalDate to) {
        return jdbc.queryForObject("""
                SELECT coalesce(-sum(total_cost) FILTER (WHERE movement_type = 'ISSUED'), 0) AS issued,
                       coalesce(sum(total_cost) FILTER (WHERE movement_type = 'RETURNED'), 0) AS returned
                  FROM stock_movements
                 WHERE movement_type IN ('ISSUED', 'RETURNED')""" + RANGE.formatted("movement_date"), range(from, to),
                (rs, i) -> {
                    BigDecimal issued = money(rs, "issued");
                    BigDecimal returned = money(rs, "returned");
                    return new MaterialUse(issued, returned, issued.subtract(returned));
                });
    }

    // by confirmed_on; drafts and voided ones aren't a cost
    public Total commissionConfirmed(LocalDate from, LocalDate to) {
        return total("SELECT count(*) AS n, coalesce(sum(commission_amount), 0) AS total FROM commission_arrangements "
                + "WHERE status = 'CONFIRMED'" + RANGE.formatted("confirmed_on"), from, to);
    }

    // by payment_date, voided ones skipped
    public Total commissionPaid(LocalDate from, LocalDate to) {
        return total("SELECT count(*) AS n, coalesce(sum(amount), 0) AS total FROM commission_payments WHERE voided_at IS NULL"
                + RANGE.formatted("payment_date"), from, to);
    }

    public List<MonthAmount> revenueByMonth(LocalDate from, LocalDate to) {
        return months("""
                SELECT date_trunc('month', i.billed_on)::date AS month,
                       coalesce(sum(GREATEST(i.amount - coalesce(d.applied_amount, 0), 0)), 0) AS total
                  FROM installments i
                  LEFT JOIN delay_deduction_terms d ON d.applied_installment_id = i.id
                 WHERE i.billed_on IS NOT NULL""" + RANGE.formatted("i.billed_on") + " GROUP BY 1", from, to);
    }

    public List<MonthAmount> receiptsByMonth(LocalDate from, LocalDate to) {
        return months("SELECT date_trunc('month', payment_date)::date AS month, sum(amount) AS total FROM client_payments "
                + "WHERE voided_at IS NULL" + RANGE.formatted("payment_date") + " GROUP BY 1", from, to);
    }

    // expenses can't be voided, so a plain sum is right
    public List<MonthAmount> expensesByMonth(LocalDate from, LocalDate to) {
        return months("SELECT date_trunc('month', expense_date)::date AS month, sum(amount) AS total FROM expenses "
                + "WHERE TRUE" + RANGE.formatted("expense_date") + " GROUP BY 1", from, to);
    }

    // same rows ExpenseService.summary adds up
    public List<CategoryAmount> projectExpensesByCategory(long projectId) {
        return jdbc.query("""
                SELECT c.name AS category, count(*) AS n, sum(e.amount) AS total
                  FROM expenses e JOIN expense_categories c ON c.id = e.category_id
                 WHERE e.scope = 'PROJECT' AND e.project_id = :projectId
                 GROUP BY c.name ORDER BY c.name""", new MapSqlParameterSource("projectId", projectId),
                (rs, i) -> new CategoryAmount(rs.getString("category"), rs.getLong("n"), money(rs, "total")));
    }

    private static final String PROJECTS = "SELECT p.id, p.code, p.name, p.project_type, p.status, p.contract_value, p.client_id, "
            + "c.name AS client_name FROM projects p JOIN clients c ON c.id = p.client_id";

    private static ProjectRef projectRef(ResultSet rs, int row) throws SQLException {
        return new ProjectRef(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("project_type"),
                rs.getString("status"), money(rs, "contract_value"), rs.getLong("client_id"), rs.getString("client_name"));
    }

    private Total total(String sql, LocalDate from, LocalDate to) {
        return jdbc.queryForObject(sql, range(from, to), (rs, i) -> new Total(rs.getLong("n"), money(rs, "total")));
    }

    private List<MonthAmount> months(String sql, LocalDate from, LocalDate to) {
        return jdbc.query(sql + " ORDER BY 1", range(from, to), (rs, i) -> new MonthAmount(rs.getObject("month", LocalDate.class),
                money(rs, "total")));
    }

    private static MapSqlParameterSource range(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource().addValue("from", from, Types.DATE).addValue("to", to, Types.DATE);
    }

    private static BigDecimal money(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return Money.amount(value == null ? BigDecimal.ZERO : value);
    }
}
