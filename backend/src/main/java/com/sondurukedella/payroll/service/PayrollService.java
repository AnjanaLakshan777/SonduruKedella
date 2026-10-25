package com.sondurukedella.payroll.service;

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.employees.dto.EmployeeResponse;
import com.sondurukedella.employees.service.AttendanceService;
import com.sondurukedella.employees.service.EmployeeService;
import com.sondurukedella.payroll.dto.EmployeePayrollHistory;
import com.sondurukedella.payroll.dto.PayrollAdjustmentRequest;
import com.sondurukedella.payroll.dto.PayrollCorrectionRequest;
import com.sondurukedella.payroll.dto.PayrollCorrectionResponse;
import com.sondurukedella.payroll.dto.PayrollLineDetail;
import com.sondurukedella.payroll.dto.PayrollLineResponse;
import com.sondurukedella.payroll.dto.PayrollLineStatus;
import com.sondurukedella.payroll.dto.PayrollPaymentRequest;
import com.sondurukedella.payroll.dto.PayrollPaymentResponse;
import com.sondurukedella.payroll.dto.PayrollWeekResponse;
import com.sondurukedella.payroll.dto.PayrollWeekSummary;
import com.sondurukedella.payroll.dto.ProjectLabourResponse;
import com.sondurukedella.payroll.dto.VoidRequest;
import com.sondurukedella.payroll.entity.PayrollAdjustment;
import com.sondurukedella.payroll.entity.PayrollCorrection;
import com.sondurukedella.payroll.entity.PayrollLine;
import com.sondurukedella.payroll.entity.PayrollLineCost;
import com.sondurukedella.payroll.entity.PayrollPayment;
import com.sondurukedella.payroll.mapper.PayrollMapper;
import com.sondurukedella.payroll.repository.PayrollAdjustmentRepository;
import com.sondurukedella.payroll.repository.PayrollCorrectionRepository;
import com.sondurukedella.payroll.repository.PayrollLineCostRepository;
import com.sondurukedella.payroll.repository.PayrollLineRepository;
import com.sondurukedella.payroll.repository.PayrollPaymentRepository;
import com.sondurukedella.payroll.service.PayrollCalculator.Cost;
import com.sondurukedella.payroll.service.PayrollLedger.WorkerWeek;
import com.sondurukedella.payroll.validation.PayrollRules;
import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.entity.ProjectType;
import com.sondurukedella.projects.service.ProjectService;
import com.sondurukedella.settings.service.PaymentMethodService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Weekly payroll. A worker's week is live (worked out from attendance, overtime, advances and the
// adjustment) until it is first paid; that payment freezes it into payroll_lines, and from then on it
// changes only by corrections. Every write locks the worker's employees row first, so payments, the
// freeze, advance recoveries and attendance for one worker happen one at a time.
//
// Where labour cost lives, so nothing is counted twice: the cost is the gross wage, split by project
// (payroll_line_costs once frozen) plus corrections. Advances are cash ahead of pay, recovered from
// net pay - not a cost. Payments settle the wage - not a cost. Labour can't be entered as a manual
// expense (the category is system-generated).
@Service
@RequiredArgsConstructor
public class PayrollService {

    public static final int MAX_WEEKS = 53;

    private final PayrollLedger ledger;
    private final PayrollLineRepository lines;
    private final PayrollLineCostRepository lineCosts;
    private final PayrollAdjustmentRepository adjustments;
    private final PayrollCorrectionRepository corrections;
    private final PayrollPaymentRepository payments;
    private final PayrollMapper mapper;
    private final EmployeeService employees;
    private final AttendanceService attendance;
    private final ProjectService projectService;
    private final PaymentMethodService paymentMethods;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PayrollWeekResponse week(LocalDate weekStart) {
        PayrollLedger.requireMonday(weekStart);
        List<PayrollLineResponse> rows = ledger.week(weekStart).stream().map(this::toResponse).toList();
        LocalDate payday = ledger.payday(weekStart);
        return new PayrollWeekResponse(weekStart, payday, !ledger.today().isBefore(payday), rows.size(),
                total(rows, PayrollLineResponse::grossPay), total(rows, PayrollLineResponse::advanceDeduction),
                total(rows, PayrollLineResponse::otherDeduction), total(rows, PayrollLineResponse::netPay),
                total(rows, PayrollLineResponse::corrections), total(rows, PayrollLineResponse::paid),
                total(rows, PayrollLineResponse::remaining), rows);
    }

    // the period summary: one row per week, newest first
    @Transactional(readOnly = true)
    public List<PayrollWeekSummary> weeks(LocalDate from, LocalDate to) {
        requireWeeks(from, to);
        List<PayrollWeekSummary> result = new ArrayList<>();
        for (LocalDate ws = to; !ws.isBefore(from); ws = ws.minusWeeks(1)) {
            PayrollWeekResponse w = week(ws);
            result.add(new PayrollWeekSummary(w.weekStart(), w.weekEnd(), w.payable(), w.workers(), w.grossPay(), w.netPay(),
                    w.corrections(), w.paid(), w.remaining()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public PayrollLineDetail line(LocalDate weekStart, long employeeId) {
        PayrollLedger.requireMonday(weekStart);
        requireEmployee(employeeId);
        return detail(ledger.workerWeek(weekStart, employeeId));
    }

    @Transactional(readOnly = true)
    public EmployeePayrollHistory history(long employeeId, LocalDate from, LocalDate to) {
        EmployeeResponse employee = requireEmployee(employeeId);
        LocalDate current = PayrollRules.weekStart(ledger.today());
        LocalDate last = to != null ? to : current;
        LocalDate first = from != null ? from : last.minusWeeks(11);
        requireWeeks(first, last);
        List<PayrollLineResponse> weeks = new ArrayList<>();
        for (LocalDate ws = last; !ws.isBefore(first); ws = ws.minusWeeks(1)) {
            ledger.recorded(ws, employeeId)
                    .ifPresent(w -> weeks.add(toResponse(w)));
        }
        // what is still to be recovered as of today, whichever week does it
        BigDecimal outstanding = ledger.balances(List.of(employeeId), ledger.today()).getOrDefault(employeeId, List.of()).stream()
                .map(PayrollLedger.AdvanceBalance::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new EmployeePayrollHistory(employeeId, employee.name(), Money.amount(outstanding), weeks);
    }

    // DATABASE.md "Project costs": frozen weeks from their split plus corrections; open weeks priced live.
    @Transactional(readOnly = true)
    public ProjectLabourResponse projectLabour(long projectId) {
        projectService.requireExists(projectId);
        List<ProjectLabourResponse.Week> rows = new ArrayList<>();
        List<PayrollLineCost> frozenCosts = lineCosts.findByProjectId(projectId);
        Map<Long, PayrollLine> frozenLines = lines.findByIdIn(frozenCosts.stream().map(PayrollLineCost::getPayrollLineId).toList())
                .stream().collect(Collectors.toMap(PayrollLine::getId, Function.identity()));
        List<PayrollCorrection> fixes = corrections.findByProjectIdAndVoidedAtIsNull(projectId);
        frozenLines.putAll(lines.findByIdIn(fixes.stream().map(PayrollCorrection::getPayrollLineId).toList()).stream()
                .collect(Collectors.toMap(PayrollLine::getId, Function.identity())));
        Map<Long, EmployeeResponse> people = new HashMap<>(employees.findAll(
                frozenLines.values().stream().map(PayrollLine::getEmployeeId).collect(Collectors.toSet())));

        BigDecimal paidWeeks = BigDecimal.ZERO.setScale(2);
        for (PayrollLineCost c : frozenCosts) {
            PayrollLine l = frozenLines.get(c.getPayrollLineId());
            rows.add(new ProjectLabourResponse.Week(l.getWeekStart(), l.getEmployeeId(), people.get(l.getEmployeeId()).name(), true, c.getAmount()));
            paidWeeks = paidWeeks.add(c.getAmount());
        }
        BigDecimal corrected = Money.amount(fixes.stream().map(PayrollCorrection::getWageDelta).reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal openWeeks = BigDecimal.ZERO.setScale(2);
        for (AttendanceService.WorkerWeek ww : attendance.workerWeeksOnProject(projectId)) {
            WorkerWeek w = ledger.workerWeek(ww.weekStart(), ww.employeeId());
            if (w.frozen() != null) {
                continue; // already counted from its frozen split
            }
            BigDecimal amount = w.live().costs().stream().filter(c -> c.isFor(projectId)).map(Cost::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (amount.signum() > 0) {
                rows.add(new ProjectLabourResponse.Week(ww.weekStart(), ww.employeeId(), w.employee().name(), false, amount));
                openWeeks = openWeeks.add(amount);
            }
        }
        rows.sort(Comparator.comparing(ProjectLabourResponse.Week::weekStart).thenComparing(ProjectLabourResponse.Week::employee));
        return new ProjectLabourResponse(projectId, paidWeeks, corrected, openWeeks, paidWeeks.add(corrected).add(openWeeks), rows);
    }

    // The owner's overrides for a week not yet paid. A paid week is frozen (the V1 trigger says so too).
    @Transactional
    public PayrollLineDetail setAdjustment(LocalDate weekStart, long employeeId, PayrollAdjustmentRequest request) {
        PayrollLedger.requireMonday(weekStart);
        lock(employeeId);
        requireOpen(weekStart, employeeId);
        PayrollAdjustment adjustment = adjustments.findByEmployeeIdAndWeekStart(employeeId, weekStart).orElse(null);
        if (adjustment == null) {
            if (request.version() != null) {
                throw new ConflictException("This week's adjustment was removed or never saved. Reload and try again.");
            }
            adjustment = new PayrollAdjustment();
            adjustment.setEmployeeId(employeeId);
            adjustment.setWeekStart(weekStart);
        } else {
            ConflictException.requireCurrentVersion(request.version(), adjustment.getVersion());
        }
        adjustment.setAdvanceDeduction(request.advanceDeduction() == null ? null : Money.amount(request.advanceDeduction()));
        adjustment.setOtherDeduction(Money.amount(request.otherDeduction() == null ? BigDecimal.ZERO : request.otherDeduction()));
        adjustment.setOtherNote(PayrollRules.trimToNull(request.otherNote()));
        adjustments.saveAndFlush(adjustment);
        return detail(ledger.workerWeek(weekStart, employeeId));
    }

    // The first payment freezes the week; then every payment is capped at what is still owed.
    @Transactional
    public PaymentResult recordPayment(LocalDate weekStart, long employeeId, PayrollPaymentRequest request) {
        PayrollLedger.requireMonday(weekStart);
        lock(employeeId);
        Optional<PayrollPayment> previous = payments.findByRequestId(request.requestId());
        if (previous.isPresent()) {
            return new PaymentResult(replay(previous.get(), weekStart, employeeId, request), false);
        }
        if (!paymentMethods.isActive(request.paymentMethodId())) {
            throw new InvalidRequestException("Choose a payment method from the Settings list.");
        }
        WorkerWeek week = ledger.workerWeek(weekStart, employeeId);
        PayrollLine line = week.frozen() != null ? week.frozen() : freezeForPayment(week);
        BigDecimal remaining = week.frozen() != null ? week.owed().subtract(week.paid()) : line.getNetPay();
        if (request.amount().compareTo(remaining) > 0) {
            throw new InvalidRequestException(remaining.signum() > 0
                    ? "More than the " + Money.format(remaining) + " still to pay for this week."
                    : "Nothing is left to pay for this week.");
        }
        PayrollPayment saved = payments.saveAndFlush(new PayrollPayment(line.getId(), request.paymentDate(), Money.amount(request.amount()),
                request.paymentMethodId(), PayrollRules.trimToNull(request.reference()), PayrollRules.trimToNull(request.notes()),
                request.requestId()));
        return new PaymentResult(mapper.toResponse(saved, employeeId, weekStart, methodName(saved.getPaymentMethodId())), true);
    }

    // Voiding stops a payment counting; the week stays frozen (it was paid once, and its snapshot is
    // what any later payment settles).
    @Transactional
    public PayrollPaymentResponse voidPayment(LocalDate weekStart, long employeeId, long paymentId, VoidRequest request) {
        lock(employeeId);
        PayrollLine line = lines.findByEmployeeIdAndWeekStart(employeeId, weekStart)
                .orElseThrow(() -> new NotFoundException("Payment not found."));
        PayrollPayment payment = payments.findById(paymentId).filter(p -> p.getPayrollLineId().equals(line.getId()))
                .orElseThrow(() -> new NotFoundException("Payment not found."));
        if (payment.isVoided()) {
            throw new ConflictException("This payment was already voided.");
        }
        ConflictException.requireCurrentVersion(request.version(), payment.getVersion());
        payment.markVoided(OffsetDateTime.now(clock), request.reason().strip());
        payment = payments.saveAndFlush(payment);
        return mapper.toResponse(payment, employeeId, weekStart, methodName(payment.getPaymentMethodId()));
    }

    // A week whose advance deduction takes all of its pay has nothing to pay, so no payment can freeze
    // it - but its recovery must still be recorded, or the advance would be deducted again next week.
    // Closing freezes it without a payment. Only for exactly that case.
    @Transactional
    public PayrollLineDetail close(LocalDate weekStart, long employeeId) {
        PayrollLedger.requireMonday(weekStart);
        lock(employeeId);
        WorkerWeek week = ledger.workerWeek(weekStart, employeeId);
        if (week.frozen() != null) {
            throw new ConflictException("This week is already paid or closed.");
        }
        String blocked = ledger.blockedReason(week);
        if (blocked != null) {
            throw new InvalidRequestException(blocked);
        }
        if (week.live().netPay().signum() != 0) {
            throw new InvalidRequestException("Only a week with nothing to pay can be closed; record a payment instead.");
        }
        ledger.freeze(week);
        return detail(ledger.workerWeek(weekStart, employeeId));
    }

    // created is false when an earlier correction with the same requestId was returned instead
    public record CorrectionResult(PayrollCorrectionResponse correction, boolean created) {
    }

    @Transactional
    public CorrectionResult correct(LocalDate weekStart, long employeeId, PayrollCorrectionRequest request) {
        PayrollLedger.requireMonday(weekStart);
        lock(employeeId);
        // checked after the worker lock, so concurrent resends find the first correction
        Optional<PayrollCorrection> previous = corrections.findByRequestId(request.requestId());
        if (previous.isPresent()) {
            PayrollCorrection p = previous.get();
            if (p.getWageDelta().compareTo(request.wageDelta()) != 0 || !p.getCorrectionDate().equals(request.correctionDate())
                    || !Objects.equals(p.getProjectId(), request.projectId())) {
                throw new ConflictException("This request id was already used for a different correction.");
            }
            return new CorrectionResult(mapper.toResponse(p, employeeId, weekStart), false);
        }
        if (request.wageDelta().signum() == 0) {
            throw new InvalidRequestException("A correction must add or take away something.");
        }
        WorkerWeek week = ledger.workerWeek(weekStart, employeeId);
        if (week.frozen() == null) {
            throw new InvalidRequestException("Only a paid week is corrected. Change this week's attendance or overtime instead.");
        }
        if (request.projectId() != null) {
            ProjectResponse project;
            try {
                project = projectService.get(request.projectId());
            } catch (NotFoundException ex) {
                throw new InvalidRequestException("The selected project does not exist.");
            }
            if (project.type() != ProjectType.CONSTRUCTION) {
                throw new InvalidRequestException("Labour can only be booked to construction projects.");
            }
        }
        BigDecimal delta = Money.amount(request.wageDelta());
        requireAllowedChange(week, request.projectId(), delta);
        PayrollCorrection saved = corrections.saveAndFlush(new PayrollCorrection(week.frozen().getId(), request.correctionDate(), delta,
                request.projectId(), request.reason().strip(), request.requestId()));
        return new CorrectionResult(mapper.toResponse(saved, employeeId, weekStart), true);
    }

    // The way to change a correction: void it (with a reason, kept in the history) and record a new one.
    // Voiding takes the delta back out, so it must pass the same checks as a correction of −delta.
    @Transactional
    public PayrollCorrectionResponse voidCorrection(LocalDate weekStart, long employeeId, long correctionId, VoidRequest request) {
        PayrollLedger.requireMonday(weekStart);
        lock(employeeId);
        WorkerWeek week = ledger.workerWeek(weekStart, employeeId);
        PayrollCorrection correction = corrections.findById(correctionId)
                .filter(c -> week.frozen() != null && c.getPayrollLineId().equals(week.frozen().getId()))
                .orElseThrow(() -> new NotFoundException("Correction not found."));
        if (correction.isVoided()) {
            throw new ConflictException("This correction was already voided.");
        }
        ConflictException.requireCurrentVersion(request.version(), correction.getVersion());
        requireAllowedChange(week, correction.getProjectId(), correction.getWageDelta().negate());
        correction.markVoided(OffsetDateTime.now(clock), request.reason().strip());
        return mapper.toResponse(corrections.saveAndFlush(correction), employeeId, weekStart);
    }

    // A change can't make the week owe less than nothing, and a reduction can't take a project (or the
    // company share) below zero labour for the week.
    private static void requireAllowedChange(WorkerWeek week, Long projectId, BigDecimal delta) {
        if (week.owed().add(delta).signum() < 0) {
            throw new InvalidRequestException("That would make the week owe less than nothing (" + Money.format(week.owed()) + " owed now).");
        }
        BigDecimal share = week.costs().stream().filter(c -> c.isFor(projectId)).map(Cost::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(week.corrections().stream().filter(c -> Objects.equals(c.getProjectId(), projectId))
                        .map(PayrollCorrection::getWageDelta).reduce(BigDecimal.ZERO, BigDecimal::add));
        if (delta.signum() < 0 && share.add(delta).signum() < 0) {
            throw new InvalidRequestException("Only " + Money.format(share) + " of this week's labour is booked "
                    + (projectId == null ? "to no project" : "to that project") + ".");
        }
    }

    private PayrollLine freezeForPayment(WorkerWeek week) {
        String blocked = ledger.blockedReason(week);
        if (blocked != null) {
            throw new InvalidRequestException(blocked);
        }
        if (week.live().netPay().signum() == 0) {
            throw new InvalidRequestException("Nothing to pay: the advance deduction takes the whole week. Close the week instead.");
        }
        return ledger.freeze(week);
    }

    private void requireOpen(LocalDate weekStart, long employeeId) {
        if (lines.findByEmployeeIdAndWeekStart(employeeId, weekStart).isPresent()) {
            throw new ConflictException("This week has been paid, so it can't change. Record a payroll correction instead.");
        }
    }

    private EmployeeResponse lock(long employeeId) {
        return employees.lockForPayroll(employeeId).orElseThrow(() -> new NotFoundException("Employee not found."));
    }

    private EmployeeResponse requireEmployee(long employeeId) {
        return employees.find(employeeId).orElseThrow(() -> new NotFoundException("Employee not found."));
    }

    // A resent submission gets the first payment back; the same id for something else is a 409.
    private PayrollPaymentResponse replay(PayrollPayment previous, LocalDate weekStart, long employeeId, PayrollPaymentRequest request) {
        PayrollLine line = lines.findById(previous.getPayrollLineId()).orElseThrow();
        boolean same = line.getEmployeeId() == employeeId && line.getWeekStart().equals(weekStart)
                && previous.getAmount().compareTo(request.amount()) == 0
                && previous.getPaymentDate().equals(request.paymentDate())
                && previous.getPaymentMethodId().equals(request.paymentMethodId());
        if (!same) {
            throw new ConflictException("This request id was already used for a different payment.");
        }
        return mapper.toResponse(previous, employeeId, weekStart, methodName(previous.getPaymentMethodId()));
    }

    private PayrollLineDetail detail(WorkerWeek w) {
        Map<Long, String> methods = paymentMethods.findNames(w.payments().stream().map(PayrollPayment::getPaymentMethodId)
                .collect(Collectors.toSet()));
        return new PayrollLineDetail(toResponse(w),
                w.payments().stream().map(p -> mapper.toResponse(p, w.employee().id(), w.weekStart(), methods.get(p.getPaymentMethodId()))).toList(),
                w.corrections().stream().map(c -> mapper.toResponse(c, w.employee().id(), w.weekStart())).toList(),
                w.recoveries().stream().map(r -> new PayrollLineDetail.Recovery(r.getAdvanceId(), r.getAmount())).toList());
    }

    PayrollLineResponse toResponse(WorkerWeek w) {
        boolean payable = !ledger.today().isBefore(w.weekEnd());
        BigDecimal owed = w.owed();
        BigDecimal paid = w.paid();
        BigDecimal remaining = owed.subtract(paid).max(BigDecimal.ZERO.setScale(2));
        BigDecimal overpaid = paid.subtract(owed).max(BigDecimal.ZERO.setScale(2));
        PayrollLineStatus status;
        if (w.frozen() != null) {
            status = paid.signum() == 0 && owed.signum() > 0 ? PayrollLineStatus.READY_TO_PAY
                    : remaining.signum() > 0 ? PayrollLineStatus.PARTIALLY_PAID : PayrollLineStatus.PAID;
        } else {
            status = !payable ? PayrollLineStatus.DRAFT
                    : w.live().grossPay().signum() == 0 ? PayrollLineStatus.NOTHING_TO_PAY : PayrollLineStatus.READY_TO_PAY;
        }
        List<PayrollLineResponse.LabourCost> costs = w.costs().stream()
                .map(c -> new PayrollLineResponse.LabourCost(c.projectId(), c.amount())).toList();
        EmployeeResponse e = w.employee();
        if (w.frozen() != null) {
            PayrollLine l = w.frozen();
            return new PayrollLineResponse(e.id(), e.name(), e.role(), w.weekStart(), l.getWeekEnd(), status, true, l.getPresentDays(),
                    l.getHalfDays(), null, l.getPayableDays(), l.getDailyWage(), l.getBasePay(), l.getOvertimeHours(), null,
                    l.getOvertimePay(), l.getGrossPay(), null, l.getAdvanceDeduction(), l.getOtherDeduction(), l.getOtherNote(),
                    l.getNetPay(), w.correctionTotal(), owed, paid, remaining, overpaid, costs, null);
        }
        PayrollCalculator.Result r = w.live();
        return new PayrollLineResponse(e.id(), e.name(), e.role(), w.weekStart(), w.weekEnd(), status, false, r.presentDays(),
                r.halfDays(), r.absentDays(), r.payableDays(), e.dailyWage(), r.basePay(), r.overtimeHours(), r.pendingOvertimeHours(),
                r.overtimePay(), r.grossPay(), r.advancesOutstanding(), r.advanceDeduction(), r.otherDeduction(),
                w.adjustment() == null ? null : w.adjustment().getOtherNote(), r.netPay(), w.correctionTotal(), owed, paid, remaining,
                overpaid, costs, ledger.blockedReason(w));
    }

    private static void requireWeeks(LocalDate from, LocalDate to) {
        PayrollLedger.requireMonday(from);
        PayrollLedger.requireMonday(to);
        if (from.isAfter(to)) {
            throw new InvalidRequestException("from must be on or before to.");
        }
        if (ChronoUnit.WEEKS.between(from, to) >= MAX_WEEKS) {
            throw new InvalidRequestException("Ask for at most " + MAX_WEEKS + " weeks at a time.");
        }
    }

    private static BigDecimal total(List<PayrollLineResponse> rows, Function<PayrollLineResponse, BigDecimal> field) {
        return Money.amount(rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private String methodName(long id) {
        return paymentMethods.findNames(List.of(id)).get(id);
    }

    // created is false when an earlier payment with the same requestId was returned instead
    public record PaymentResult(PayrollPaymentResponse payment, boolean created) {
    }
}
