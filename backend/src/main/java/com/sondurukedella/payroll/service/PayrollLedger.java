package com.sondurukedella.payroll.service;

import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.employees.dto.EmployeeResponse;
import com.sondurukedella.employees.dto.PayrollInputs;
import com.sondurukedella.employees.service.AttendanceService;
import com.sondurukedella.employees.service.EmployeeService;
import com.sondurukedella.payroll.entity.AdvanceRecovery;
import com.sondurukedella.payroll.entity.PayrollAdjustment;
import com.sondurukedella.payroll.entity.PayrollCorrection;
import com.sondurukedella.payroll.entity.PayrollLine;
import com.sondurukedella.payroll.entity.PayrollLineCost;
import com.sondurukedella.payroll.entity.PayrollPayment;
import com.sondurukedella.payroll.entity.SalaryAdvance;
import com.sondurukedella.payroll.repository.AdvanceRecoveryRepository;
import com.sondurukedella.payroll.repository.AdvanceRecoveryRepository.AdvanceTotal;
import com.sondurukedella.payroll.repository.PayrollAdjustmentRepository;
import com.sondurukedella.payroll.repository.PayrollCorrectionRepository;
import com.sondurukedella.payroll.repository.PayrollLineCostRepository;
import com.sondurukedella.payroll.repository.PayrollLineRepository;
import com.sondurukedella.payroll.repository.PayrollPaymentRepository;
import com.sondurukedella.payroll.repository.SalaryAdvanceRepository;
import com.sondurukedella.payroll.service.PayrollCalculator.Cost;
import com.sondurukedella.payroll.service.PayrollCalculator.Result;
import com.sondurukedella.settings.entity.Payday;
import com.sondurukedella.settings.service.WageSettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Builds workers' weeks: a frozen week from its payroll_lines snapshot, any other week live from the
// attendance and overtime that employees hands over (never its tables), the week's adjustment and the
// advances not yet recovered. freeze() writes the snapshot; callers hold the worker's row lock.
@Component
@RequiredArgsConstructor
class PayrollLedger {

    private final PayrollLineRepository lines;
    private final PayrollLineCostRepository lineCosts;
    private final AdvanceRecoveryRepository recoveries;
    private final SalaryAdvanceRepository advances;
    private final PayrollAdjustmentRepository adjustments;
    private final PayrollCorrectionRepository corrections;
    private final PayrollPaymentRepository payments;
    private final AttendanceService attendance;
    private final EmployeeService employees;
    private final WageSettingsService wageSettings;
    private final Clock clock;

    static LocalDate requireMonday(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new InvalidRequestException("A payroll week starts on a Monday; " + weekStart + " is a "
                    + weekStart.getDayOfWeek().name().charAt(0) + weekStart.getDayOfWeek().name().substring(1).toLowerCase() + ".");
        }
        return weekStart;
    }

    // payday (Settings > Wages) is the week's end for paying: Friday or Saturday of the Monday-Sunday week
    LocalDate payday(LocalDate weekStart) {
        return weekStart.plusDays(wageSettings.get().payday() == Payday.FRIDAY ? 4 : 5);
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    // every worker with something in the week: a day, an overtime entry or a paid line
    List<WorkerWeek> week(LocalDate weekStart) {
        return load(weekStart, null);
    }

    // one worker's week, if it has anything in it
    Optional<WorkerWeek> recorded(LocalDate weekStart, long employeeId) {
        return load(weekStart, employeeId).stream().findFirst();
    }

    // one worker's week, even an empty one
    WorkerWeek workerWeek(LocalDate weekStart, long employeeId) {
        List<WorkerWeek> found = load(weekStart, employeeId);
        if (!found.isEmpty()) {
            return found.getFirst();
        }
        return emptyWeek(employees.find(employeeId).orElseThrow(), weekStart);
    }

    private List<WorkerWeek> load(LocalDate weekStart, Long employeeId) {
        LocalDate sunday = weekStart.plusDays(6);
        LocalDate payday = payday(weekStart);
        PayrollInputs inputs = attendance.payrollInputs(weekStart, sunday, employeeId);
        Map<Long, PayrollLine> frozen = lines.findByWeekStart(weekStart).stream()
                .filter(l -> employeeId == null || l.getEmployeeId().equals(employeeId))
                .collect(Collectors.toMap(PayrollLine::getEmployeeId, Function.identity()));
        Set<Long> ids = new HashSet<>(frozen.keySet());
        inputs.days().forEach(d -> ids.add(d.employeeId()));
        inputs.overtime().forEach(o -> ids.add(o.employeeId()));
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, EmployeeResponse> people = employees.findAll(ids);
        Map<Long, PayrollAdjustment> adjustmentsByEmployee = adjustments.findByWeekStart(weekStart).stream()
                .collect(Collectors.toMap(PayrollAdjustment::getEmployeeId, Function.identity()));
        Set<Long> open = ids.stream().filter(id -> !frozen.containsKey(id)).collect(Collectors.toSet());
        Map<Long, List<AdvanceBalance>> balances = open.isEmpty() ? Map.of() : balancesAfterEarlierWeeks(open, weekStart);
        Map<Long, List<PayrollInputs.Day>> days = inputs.days().stream().collect(Collectors.groupingBy(PayrollInputs.Day::employeeId));
        Map<Long, List<PayrollInputs.Overtime>> ot = inputs.overtime().stream().collect(Collectors.groupingBy(PayrollInputs.Overtime::employeeId));
        Map<Long, FrozenParts> parts = frozenParts(frozen.values());

        List<WorkerWeek> result = new ArrayList<>();
        for (Long id : ids.stream().sorted(Comparator.comparing((Long x) -> people.get(x).name()).thenComparing(x -> x)).toList()) {
            PayrollLine line = frozen.get(id);
            if (line != null) {
                FrozenParts p = parts.get(line.getId());
                result.add(new WorkerWeek(people.get(id), weekStart, line.getWeekEnd(), line, null, null, List.of(),
                        p.costs(), p.corrections(), p.payments(), p.recoveries()));
            } else {
                result.add(live(people.get(id), weekStart, payday, days.getOrDefault(id, List.of()), ot.getOrDefault(id, List.of()),
                        adjustmentsByEmployee.get(id), balances.getOrDefault(id, List.of())));
            }
        }
        return result;
    }

    private WorkerWeek live(EmployeeResponse employee, LocalDate weekStart, LocalDate payday, List<PayrollInputs.Day> days,
            List<PayrollInputs.Overtime> ot, PayrollAdjustment adjustment, List<AdvanceBalance> balances) {
        if (balances == null) {
            balances = balancesAfterEarlierWeeks(List.of(employee.id()), weekStart).getOrDefault(employee.id(), List.of());
        }
        BigDecimal outstanding = balances.stream().map(AdvanceBalance::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
        Result r = PayrollCalculator.calculate(employee.dailyWage(),
                days.stream().map(d -> new PayrollCalculator.Day(d.status(), d.projectId())).toList(),
                ot.stream().map(o -> new PayrollCalculator.Overtime(o.hours(), o.hourlyRate(), o.approved(), o.projectId())).toList(),
                outstanding, adjustment == null ? null : adjustment.getAdvanceDeduction(),
                adjustment == null ? null : adjustment.getOtherDeduction());
        return new WorkerWeek(employee, weekStart, payday, null, r, adjustment, balances, List.of(), List.of(), List.of(), List.of());
    }

    // an empty live week for a worker with nothing recorded, with the advances it would recover
    WorkerWeek emptyWeek(EmployeeResponse employee, LocalDate weekStart) {
        return live(employee, weekStart, payday(weekStart), List.of(), List.of(), null, null);
    }

    // advances not yet recovered, oldest first, per worker: what a week paid on `payday` may deduct
    Map<Long, List<AdvanceBalance>> balances(Collection<Long> employeeIds, LocalDate payday) {
        List<SalaryAdvance> given = advances.findActiveUpTo(employeeIds, payday);
        if (given.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> recovered = recoveries.recoveredByAdvance(given.stream().map(SalaryAdvance::getId).toList()).stream()
                .collect(Collectors.toMap(AdvanceTotal::getAdvanceId, AdvanceTotal::getTotal));
        Map<Long, List<AdvanceBalance>> byEmployee = new LinkedHashMap<>();
        for (SalaryAdvance a : given) {
            BigDecimal left = a.getAmount().subtract(recovered.getOrDefault(a.getId(), BigDecimal.ZERO));
            if (left.signum() > 0) {
                byEmployee.computeIfAbsent(a.getEmployeeId(), k -> new ArrayList<>()).add(new AdvanceBalance(a.getId(), a.getPaymentDate(), left));
            }
        }
        return byEmployee;
    }

    // What a live week may still deduct: the advances not yet recovered, less what the worker's earlier
    // weeks that aren't paid yet will take first (oldest week first, as lib/finance.ts computePayroll does).
    // Without this every unpaid week deducted the same advance again, so the open weeks added up to too little.
    Map<Long, List<AdvanceBalance>> balancesAfterEarlierWeeks(Collection<Long> employeeIds, LocalDate weekStart) {
        Map<Long, List<AdvanceBalance>> base = balances(employeeIds, payday(weekStart));
        if (base.isEmpty()) {
            return base;
        }
        // only weeks paid on or after the oldest open advance can deduct it
        LocalDate oldest = base.values().stream().flatMap(List::stream).map(AdvanceBalance::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate from = oldest.with(DayOfWeek.MONDAY);
        LocalDate lastEarlier = weekStart.minusWeeks(1);
        if (from.isAfter(lastEarlier)) {
            return base;
        }
        Set<Long> ids = base.keySet();
        PayrollInputs inputs = attendance.payrollInputs(from, weekStart.minusDays(1), null);
        Set<String> frozenWeeks = lines.findByWeekStartBetween(from, lastEarlier).stream()
                .map(l -> l.getEmployeeId() + ":" + l.getWeekStart()).collect(Collectors.toSet());
        Map<String, PayrollAdjustment> adjusted = adjustments.findByWeekStartBetweenOrderByWeekStartAscIdAsc(from, lastEarlier).stream()
                .collect(Collectors.toMap(a -> a.getEmployeeId() + ":" + a.getWeekStart(), Function.identity(), (a, b) -> b));
        Map<String, List<PayrollInputs.Day>> days = inputs.days().stream().filter(d -> ids.contains(d.employeeId()))
                .collect(Collectors.groupingBy(d -> d.employeeId() + ":" + d.date().with(DayOfWeek.MONDAY)));
        Map<String, List<PayrollInputs.Overtime>> ot = inputs.overtime().stream().filter(o -> ids.contains(o.employeeId()))
                .collect(Collectors.groupingBy(o -> o.employeeId() + ":" + o.date().with(DayOfWeek.MONDAY)));
        Map<Long, EmployeeResponse> people = employees.findAll(ids);

        Map<Long, List<AdvanceBalance>> left = new LinkedHashMap<>();
        base.forEach((id, list) -> left.put(id, new ArrayList<>(list)));
        for (LocalDate ws = from; !ws.isAfter(lastEarlier); ws = ws.plusWeeks(1)) {
            LocalDate wsPayday = payday(ws);
            for (Long id : ids) {
                String key = id + ":" + ws;
                List<PayrollInputs.Day> d = days.getOrDefault(key, List.of());
                List<PayrollInputs.Overtime> o = ot.getOrDefault(key, List.of());
                // a frozen week already recorded its recoveries; a week with nothing in it deducts nothing
                if (frozenWeeks.contains(key) || (d.isEmpty() && o.isEmpty())) {
                    continue;
                }
                List<AdvanceBalance> mine = left.get(id);
                BigDecimal usable = mine.stream().filter(b -> !b.date().isAfter(wsPayday))
                        .map(AdvanceBalance::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (usable.signum() <= 0) {
                    continue;
                }
                PayrollAdjustment adj = adjusted.get(key);
                Result r = PayrollCalculator.calculate(people.get(id).dailyWage(),
                        d.stream().map(x -> new PayrollCalculator.Day(x.status(), x.projectId())).toList(),
                        o.stream().map(x -> new PayrollCalculator.Overtime(x.hours(), x.hourlyRate(), x.approved(), x.projectId())).toList(),
                        usable, adj == null ? null : adj.getAdvanceDeduction(), adj == null ? null : adj.getOtherDeduction());
                left.put(id, consume(mine, r.advanceDeduction(), wsPayday));
            }
        }
        left.values().removeIf(List::isEmpty);
        return left;
    }

    // takes `amount` off the oldest advances usable by `payday`, dropping the ones used up
    private static List<AdvanceBalance> consume(List<AdvanceBalance> balances, BigDecimal amount, LocalDate payday) {
        List<AdvanceBalance> out = new ArrayList<>();
        BigDecimal todo = amount;
        for (AdvanceBalance b : balances) {
            if (todo.signum() > 0 && !b.date().isAfter(payday)) {
                BigDecimal take = todo.min(b.outstanding());
                todo = todo.subtract(take);
                if (b.outstanding().subtract(take).signum() > 0) {
                    out.add(new AdvanceBalance(b.advanceId(), b.date(), b.outstanding().subtract(take)));
                }
            } else {
                out.add(b);
            }
        }
        return out;
    }

    private Map<Long, FrozenParts> frozenParts(Collection<PayrollLine> frozen) {
        if (frozen.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = frozen.stream().map(PayrollLine::getId).toList();
        Map<Long, List<PayrollLineCost>> costs = lineCosts.findByPayrollLineIdIn(ids).stream()
                .collect(Collectors.groupingBy(PayrollLineCost::getPayrollLineId));
        Map<Long, List<PayrollCorrection>> fixes = corrections.findByPayrollLineIdInAndVoidedAtIsNullOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(PayrollCorrection::getPayrollLineId));
        Map<Long, List<PayrollPayment>> paid = payments.findByPayrollLineIdInOrderByPaymentDateAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(PayrollPayment::getPayrollLineId));
        Map<Long, List<AdvanceRecovery>> recovered = recoveries.findByPayrollLineIdIn(ids).stream()
                .collect(Collectors.groupingBy(AdvanceRecovery::getPayrollLineId));
        return ids.stream().collect(Collectors.toMap(Function.identity(), id -> new FrozenParts(
                costs.getOrDefault(id, List.of()).stream().map(c -> new Cost(c.getProjectId(), c.getAmount()))
                        .sorted(Comparator.comparing(c -> c.projectId() == null ? Long.MIN_VALUE : c.projectId())).toList(),
                fixes.getOrDefault(id, List.of()), paid.getOrDefault(id, List.of()), recovered.getOrDefault(id, List.of()))));
    }

    // Writes the snapshot of a live week: the line, its project split and which advances it recovered
    // (oldest first). From here the week's attendance, overtime and adjustment are frozen by trigger.
    PayrollLine freeze(WorkerWeek week) {
        Result r = week.live();
        PayrollLine line = lines.saveAndFlush(PayrollLine.freeze(week.employee().id(), week.weekStart(), week.weekEnd(),
                week.employee().dailyWage(), r, week.adjustment() == null ? null : week.adjustment().getOtherNote()));
        lineCosts.saveAllAndFlush(r.costs().stream().map(c -> new PayrollLineCost(line.getId(), c.projectId(), c.amount())).toList());
        BigDecimal left = r.advanceDeduction();
        List<AdvanceRecovery> rows = new ArrayList<>();
        for (AdvanceBalance b : week.advances()) {
            if (left.signum() <= 0) {
                break;
            }
            BigDecimal take = left.min(b.outstanding());
            rows.add(new AdvanceRecovery(b.advanceId(), line.getId(), week.employee().id(), take));
            left = left.subtract(take);
        }
        recoveries.saveAllAndFlush(rows);
        return line;
    }

    // why a live week can't be frozen and paid yet, or null when it can
    String blockedReason(WorkerWeek week) {
        if (week.frozen() != null) {
            return null;
        }
        Result r = week.live();
        if (today().isBefore(week.weekEnd())) {
            return "Payday for this week is " + week.weekEnd() + ".";
        }
        if (r.grossPay().signum() == 0) {
            return "Nothing was earned this week.";
        }
        if (r.netPay().signum() < 0) {
            return "The deductions (" + Money.format(r.advanceDeduction().add(r.otherDeduction())) + ") are more than this week's pay ("
                    + Money.format(r.grossPay()) + "). Lower the other deduction.";
        }
        return null;
    }

    record AdvanceBalance(long advanceId, LocalDate date, BigDecimal outstanding) {
    }

    private record FrozenParts(List<Cost> costs, List<PayrollCorrection> corrections, List<PayrollPayment> payments,
            List<AdvanceRecovery> recoveries) {
    }

    // A worker's week: frozen (the snapshot and what hangs off it) or live (the calculation).
    record WorkerWeek(EmployeeResponse employee, LocalDate weekStart, LocalDate weekEnd, PayrollLine frozen, Result live,
            PayrollAdjustment adjustment, List<AdvanceBalance> advances, List<Cost> frozenCosts,
            List<PayrollCorrection> corrections, List<PayrollPayment> payments, List<AdvanceRecovery> recoveries) {

        BigDecimal net() {
            return frozen != null ? frozen.getNetPay() : live.netPay();
        }

        BigDecimal gross() {
            return frozen != null ? frozen.getGrossPay() : live.grossPay();
        }

        List<Cost> costs() {
            return frozen != null ? frozenCosts : live.costs();
        }

        BigDecimal correctionTotal() {
            return Money.amount(corrections.stream().map(PayrollCorrection::getWageDelta).reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        BigDecimal paid() {
            return Money.amount(payments.stream().filter(p -> !p.isVoided()).map(PayrollPayment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        BigDecimal owed() {
            return net().add(correctionTotal());
        }
    }
}
