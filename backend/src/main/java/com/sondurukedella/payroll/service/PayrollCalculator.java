package com.sondurukedella.payroll.service;

import com.sondurukedella.common.money.Money;
import com.sondurukedella.employees.entity.AttendanceStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// One worker's week, as DATABASE.md "Weekly payroll" defines it (and lib/finance.ts computePayroll does).
// Pure arithmetic, so every rule is tested on its own. Money is HALF_UP to 2 decimals.
//   payable_days  = present + 0.5 x half days
//   base_pay      = round(payable_days x daily_wage, 2)
//   overtime_pay  = sum of round(hours x rate, 2) over approved entries
//   gross         = base_pay + overtime_pay
//   advance       = the owner's override or all outstanding advances, never more than either, nor than gross
//   net           = gross - advance - other deduction (can come out negative; payroll then refuses to pay)
// The project split always adds up to gross exactly: each project's days are priced on their own and the
// rounding residue (a cent or so) goes to the largest share.
public final class PayrollCalculator {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private PayrollCalculator() {
    }

    public static Result calculate(BigDecimal dailyWage, List<Day> days, List<Overtime> overtime, BigDecimal advancesOutstanding,
            BigDecimal advanceOverride, BigDecimal otherDeduction) {
        int present = 0;
        int half = 0;
        int absent = 0;
        // half-days worked per project (null = no project, i.e. yard or office work)
        Map<Long, Integer> halves = new LinkedHashMap<>();
        for (Day d : days) {
            int units = switch (d.status()) {
                case PRESENT -> {
                    present++;
                    yield 2;
                }
                case HALF_DAY -> {
                    half++;
                    yield 1;
                }
                case ABSENT -> {
                    absent++;
                    yield 0;
                }
            };
            if (units > 0) {
                halves.merge(d.projectId(), units, Integer::sum);
            }
        }
        BigDecimal payableDays = BigDecimal.valueOf(present).add(BigDecimal.valueOf(half).multiply(new BigDecimal("0.5"))).setScale(1);
        BigDecimal basePay = Money.amount(payableDays.multiply(dailyWage));

        Map<Long, BigDecimal> costs = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> e : halves.entrySet()) {
            costs.put(e.getKey(), BigDecimal.valueOf(e.getValue()).multiply(dailyWage).divide(TWO, Money.SCALE, RoundingMode.HALF_UP));
        }
        BigDecimal residue = basePay.subtract(sum(costs.values()));
        if (residue.signum() != 0 && !costs.isEmpty()) {
            Long largest = costs.entrySet().stream()
                    .max(Comparator.<Map.Entry<Long, BigDecimal>, BigDecimal>comparing(Map.Entry::getValue)
                            .thenComparing(e -> e.getKey() == null ? Long.MAX_VALUE : -e.getKey()))
                    .orElseThrow().getKey();
            costs.merge(largest, residue, BigDecimal::add);
        }

        BigDecimal overtimeHours = BigDecimal.ZERO.setScale(2);
        BigDecimal pendingHours = BigDecimal.ZERO.setScale(2);
        BigDecimal overtimePay = BigDecimal.ZERO.setScale(Money.SCALE);
        for (Overtime o : overtime) {
            if (!o.approved()) {
                pendingHours = pendingHours.add(o.hours());
                continue;
            }
            BigDecimal pay = Money.amount(o.hours().multiply(o.hourlyRate()));
            overtimeHours = overtimeHours.add(o.hours());
            overtimePay = overtimePay.add(pay);
            costs.merge(o.projectId(), pay, BigDecimal::add);
        }
        BigDecimal gross = basePay.add(overtimePay);

        BigDecimal outstanding = Money.amount(advancesOutstanding.max(BigDecimal.ZERO));
        BigDecimal wanted = advanceOverride == null ? outstanding : Money.amount(advanceOverride);
        BigDecimal advance = wanted.min(outstanding).min(gross).max(BigDecimal.ZERO.setScale(Money.SCALE));
        BigDecimal other = Money.amount(otherDeduction == null ? BigDecimal.ZERO : otherDeduction);
        BigDecimal net = gross.subtract(advance).subtract(other);

        List<Cost> split = new ArrayList<>();
        costs.entrySet().stream()
                .filter(e -> e.getValue().signum() > 0)
                .sorted(Comparator.comparing(e -> e.getKey() == null ? Long.MIN_VALUE : e.getKey()))
                .forEach(e -> split.add(new Cost(e.getKey(), e.getValue())));
        return new Result(present, half, absent, payableDays, basePay, overtimeHours, pendingHours, overtimePay, gross,
                outstanding, advance, other, net, split);
    }

    private static BigDecimal sum(Iterable<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO.setScale(Money.SCALE);
        for (BigDecimal v : values) {
            total = total.add(v);
        }
        return total;
    }

    public record Day(AttendanceStatus status, Long projectId) {
    }

    public record Overtime(BigDecimal hours, BigDecimal hourlyRate, boolean approved, Long projectId) {
    }

    // projectId null = labour not tied to a project (a company cost)
    public record Cost(Long projectId, BigDecimal amount) {

        public boolean isFor(Long project) {
            return Objects.equals(projectId, project);
        }
    }

    public record Result(
            int presentDays,
            int halfDays,
            int absentDays,
            BigDecimal payableDays,
            BigDecimal basePay,
            BigDecimal overtimeHours,
            BigDecimal pendingOvertimeHours,
            BigDecimal overtimePay,
            BigDecimal grossPay,
            BigDecimal advancesOutstanding,
            BigDecimal advanceDeduction,
            BigDecimal otherDeduction,
            BigDecimal netPay,
            List<Cost> costs) {
    }
}
