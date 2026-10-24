package com.sondurukedella.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.employees.dto.AttendanceDayRequest;
import com.sondurukedella.employees.dto.EmployeeCreateRequest;
import com.sondurukedella.employees.entity.AttendanceStatus;
import com.sondurukedella.employees.service.AttendanceService;
import com.sondurukedella.employees.service.EmployeeService;
import com.sondurukedella.payroll.dto.PayrollPaymentRequest;
import com.sondurukedella.payroll.service.PayrollService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// Not @Transactional: real transactions racing each other. Everything belongs to workers named
// PAYTEST-* and is deleted afterwards. Throwaway DB only.
@SpringBootTest
class PayrollConcurrencyIntegrationTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 9, 7);

    @Autowired
    private EmployeeService employees;

    @Autowired
    private AttendanceService attendance;

    @Autowired
    private PayrollService payroll;

    @Autowired
    private JdbcTemplate jdbc;

    private long cash;
    private long worker;

    @BeforeEach
    void setUp() {
        cash = jdbc.queryForObject("SELECT id FROM payment_methods WHERE name = 'Cash'", Long.class);
        worker = employees.create(new EmployeeCreateRequest("PAYTEST-" + UUID.randomUUID().toString().substring(0, 8), "Labourer",
                null, null, new BigDecimal("3000"), new BigDecimal("560"), LocalDate.of(2026, 1, 5), null)).id();
        for (int d = 0; d < 3; d++) {
            mark(WEEK.plusDays(d), AttendanceStatus.PRESENT); // 9,000
        }
    }

    @AfterEach
    void cleanUp() {
        String ours = "SELECT id FROM employees WHERE name LIKE 'PAYTEST-%'";
        String lines = "SELECT id FROM payroll_lines WHERE employee_id IN (" + ours + ")";
        // paid weeks can't be deleted (V1 triggers); lift them for this cleanup only
        List<String> guards = List.of("payroll_payments:trg_payroll_payments_guard", "advance_recoveries:trg_advance_recoveries_immutable",
                "payroll_line_costs:trg_payroll_line_costs_immutable", "payroll_lines:trg_payroll_lines_immutable",
                "salary_advances:trg_salary_advances_guard");
        guards.forEach(g -> jdbc.execute("ALTER TABLE " + g.split(":")[0] + " DISABLE TRIGGER " + g.split(":")[1]));
        try {
            jdbc.update("DELETE FROM payroll_payments WHERE payroll_line_id IN (" + lines + ")");
            jdbc.update("DELETE FROM advance_recoveries WHERE employee_id IN (" + ours + ")");
            jdbc.update("DELETE FROM payroll_line_costs WHERE payroll_line_id IN (" + lines + ")");
            jdbc.update("DELETE FROM payroll_corrections WHERE payroll_line_id IN (" + lines + ")");
            jdbc.update("DELETE FROM payroll_lines WHERE employee_id IN (" + ours + ")");
            jdbc.update("DELETE FROM salary_advances WHERE employee_id IN (" + ours + ")");
        } finally {
            guards.forEach(g -> jdbc.execute("ALTER TABLE " + g.split(":")[0] + " ENABLE TRIGGER " + g.split(":")[1]));
        }
        jdbc.update("DELETE FROM payroll_adjustments WHERE employee_id IN (" + ours + ")");
        jdbc.update("DELETE FROM attendance WHERE employee_id IN (" + ours + ")");
        jdbc.update("DELETE FROM overtime_entries WHERE employee_id IN (" + ours + ")");
        jdbc.update("DELETE FROM employees WHERE name LIKE 'PAYTEST-%'");
    }

    @Test
    void concurrentPaymentsCanNotOverpayAWeek() throws Exception {
        // six payments of 2,000 against 9,000 owed: exactly four fit, and the week is frozen once
        List<Outcome> outcomes = race(6, i -> () -> payroll.recordPayment(WEEK, worker, payment(UUID.randomUUID(), "2000")).created());

        assertThat(outcomes.stream().filter(o -> o.error == null)).hasSize(4);
        assertThat(outcomes.stream().filter(o -> o.error != null)).hasSize(2).allMatch(o -> o.error instanceof InvalidRequestException);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_lines WHERE employee_id = ?", Long.class, worker)).isOne();
        assertThat(paid()).isEqualByComparingTo("8000.00");
    }

    @Test
    void theSamePaymentSentManyTimesAtOnceIsRecordedOnce() throws Exception {
        UUID requestId = UUID.randomUUID();
        List<Outcome> outcomes = race(6, i -> () -> payroll.recordPayment(WEEK, worker, payment(requestId, "9000")).created());

        assertThat(outcomes).allMatch(o -> o.error == null);
        assertThat(outcomes.stream().filter(o -> o.created)).hasSize(1);
        assertThat(paid()).isEqualByComparingTo("9000.00");
    }

    @Test
    void attendanceSavedWhileTheWeekIsBeingPaidLandsBeforeTheFreezeOrIsRefused() throws Exception {
        // a fourth day and the first payment, at the same moment
        List<Outcome> outcomes = race(2, i -> i == 0
                ? () -> {
                    mark(WEEK.plusDays(3), AttendanceStatus.PRESENT);
                    return true;
                }
                : () -> payroll.recordPayment(WEEK, worker, payment(UUID.randomUUID(), "9000")).created());

        assertThat(outcomes.get(1).error).isNull();
        int frozenDays = jdbc.queryForObject("SELECT present_days FROM payroll_lines WHERE employee_id = ?", Integer.class, worker);
        int recordedDays = jdbc.queryForObject("SELECT count(*) FROM attendance WHERE employee_id = ? AND status = 'PRESENT'",
                Integer.class, worker);
        // whichever won, the paid snapshot and the register agree; the day never slips in unpaid
        assertThat(frozenDays).isEqualTo(recordedDays);
        if (outcomes.get(0).error != null) {
            assertThat(outcomes.get(0).error).isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void aRefusedPaymentLeavesTheWeekUnfrozen() {
        assertThatThrownBy(() -> payroll.recordPayment(WEEK, worker, payment(UUID.randomUUID(), "9000.01")))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_lines WHERE employee_id = ?", Long.class, worker)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_line_costs", Long.class)).isZero();
        mark(WEEK.plusDays(3), AttendanceStatus.HALF_DAY); // still open
    }

    private void mark(LocalDate day, AttendanceStatus status) {
        attendance.saveDay(day, new AttendanceDayRequest(List.of(new AttendanceDayRequest.Entry(worker, status, null, null))));
    }

    private PayrollPaymentRequest payment(UUID requestId, String amount) {
        return new PayrollPaymentRequest(requestId, WEEK.plusDays(5), new BigDecimal(amount), cash, null, null);
    }

    private BigDecimal paid() {
        return jdbc.queryForObject("SELECT coalesce(sum(p.amount), 0) FROM payroll_payments p JOIN payroll_lines l ON l.id = p.payroll_line_id "
                + "WHERE l.employee_id = ? AND p.voided_at IS NULL", BigDecimal.class, worker);
    }

    // starts every task at the same moment and records how each ended
    private static List<Outcome> race(int count, IntFunction<Callable<Boolean>> task) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<Boolean> work = task.apply(i);
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        return new Outcome(null, work.call());
                    } catch (Exception ex) {
                        return new Outcome(ex, false);
                    }
                }));
            }
            go.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private record Outcome(Exception error, boolean created) {
    }
}
